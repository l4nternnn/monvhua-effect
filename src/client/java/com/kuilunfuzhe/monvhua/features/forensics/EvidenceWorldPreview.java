package com.kuilunfuzhe.monvhua.features.forensics;

import com.kuilunfuzhe.monvhua.features.mirror.FramebufferOverride;
import com.kuilunfuzhe.monvhua.features.portal.client.PortalFramebufferOverride;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.render.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3d;
import org.lwjgl.glfw.GLFW;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

/** W1/W2 local test anchor. Server persistence is intentionally a separate stage. */
public final class EvidenceWorldPreview {
    private static ClientWorld world;
    private static Vec3d center;
    private static Quaternionf rotation;
    private static EvidencePanelPlane plane;
    private static EvidencePanel panel;
    private static EvidencePanelTexture texture;
    private static BufferAllocator allocator;
    private static VertexConsumerProvider.Immediate consumers;
    private static volatile boolean reload;
    private static boolean removeRequested;
    private static float panelWidth=1.5f;
    private static int resolutionScale=1;
    private static long feedbackUntil;
    private static String feedback="";
    private static float panelHeight(){return panelWidth*9/16;}
    private static String settings(){return "面板 "+panelWidth+" × "+panelHeight()+" 格，贴图 "+(EvidencePanelTexture.WIDTH*resolutionScale)+" × "+(EvidencePanelTexture.HEIGHT*resolutionScale);}

    public static void initialize(){
        var key=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.monvhua.evidence_world_interact",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_UNKNOWN,"category.monvhua"));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher,access)->dispatcher.register(literal("monvhua-evidence-preview")
            .then(literal("place").executes(ctx->place()))
            .then(literal("remove").executes(ctx->{clear();return 1;}))
            .then(literal("size").then(argument("width",FloatArgumentType.floatArg(.5f,8)).executes(ctx->{
                float value=FloatArgumentType.getFloat(ctx,"width");if(!Float.isFinite(value))return 0;
                panelWidth=value;if(plane!=null)updatePlane();ctx.getSource().sendFeedback(Text.literal(settings()));return 1;
            })))
            .then(literal("resolution").then(argument("scale",IntegerArgumentType.integer(1,4)).executes(ctx->{
                resolutionScale=IntegerArgumentType.getInteger(ctx,"scale");releaseGraphics();
                ctx.getSource().sendFeedback(Text.literal(settings()));return 1;
            })))
            .then(literal("status").executes(ctx->{ctx.getSource().sendFeedback(Text.literal(settings()));return 1;}))
            .then(literal("rotate").then(argument("yaw",FloatArgumentType.floatArg(-360,360))
                .then(argument("pitch",FloatArgumentType.floatArg(-360,360))
                .then(argument("roll",FloatArgumentType.floatArg(-360,360)).executes(ctx->{
                    if(panel==null)return 0;
                    rotation=new Quaternionf().rotationYXZ((float)Math.toRadians(FloatArgumentType.getFloat(ctx,"yaw")),(float)Math.toRadians(FloatArgumentType.getFloat(ctx,"pitch")),(float)Math.toRadians(FloatArgumentType.getFloat(ctx,"roll")));
                    updatePlane();return 1;
                })))))));
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(panel!=null&&(client.world!=world||client.player==null||removeRequested))clear();
            while(key.wasPressed())if(panel!=null&&client.currentScreen==null){
                var hit=hit();if(hit!=null){
                    double x=hit.u()*EvidencePanelTexture.LOGICAL_WIDTH,y=hit.v()*EvidencePanelTexture.LOGICAL_HEIGHT;
                    String label=panel.worldHoverLabel(x,y);
                    boolean handled=panel.worldClick(x,y);
                    feedback=handled?"已点击："+label:"此处没有可操作控件，请对准按钮、轮盘或卡片";
                    feedbackUntil=System.nanoTime()+900_000_000L;
                }
            }
            if(client.world==null)EvidenceSessionRouter.clear();
        });
        HudRenderCallback.EVENT.register((draw,tick)->{
            var client=MinecraftClient.getInstance();if(panel==null||client.currentScreen!=null||client.options.hudHidden)return;
            var hit=hit();if(hit==null)return;
            String label=panel.worldHoverLabel(hit.u()*EvidencePanelTexture.LOGICAL_WIDTH,hit.v()*EvidencePanelTexture.LOGICAL_HEIGHT);
            String hint=System.nanoTime()<feedbackUntil?feedback:label.isEmpty()?"已瞄准面板 · 请对准按钮、轮盘或卡片":
                key.isUnbound()?"已瞄准："+label+" · 请绑定“世界线索面板交互”键":"按 ["+key.getBoundKeyLocalizedText().getString()+"] "+label;
            int cx=draw.getScaledWindowWidth()/2,y=draw.getScaledWindowHeight()/2+18;
            int half=client.textRenderer.getWidth(hint)/2;
            draw.fill(cx-half-5,y-3,cx+half+5,y+12,0xC0101820);
            draw.drawCenteredTextWithShadow(client.textRenderer,hint,cx,y,label.isEmpty()?0xFFCBD5DF:0xFFFFD65A);
        });
        WorldRenderEvents.AFTER_ENTITIES.register(EvidenceWorldPreview::render);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client->clear());
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener(){
            public Identifier getFabricId(){return Identifier.of("monvhua","evidence_world_preview");}
            public void reload(ResourceManager manager){reload=true;}
        });
    }
    private static int place(){
        var client=MinecraftClient.getInstance();if(client.player==null||client.world==null)return 0;
        clear();world=client.world;
        center=client.player.getEyePos().add(client.player.getRotationVec(1).multiply(3));
        rotation=new Quaternionf().rotationYXZ((float)Math.toRadians(180-client.player.getYaw()),(float)Math.toRadians(-client.player.getPitch()),0);
        updatePlane();panel=new EvidencePanel(true,()->removeRequested=true);panel.resize(EvidencePanelTexture.LOGICAL_WIDTH,EvidencePanelTexture.LOGICAL_HEIGHT);
        client.player.sendMessage(Text.literal("已放置本地线索预览（不保存）；请在按键设置绑定世界线索面板交互键。"),false);return 1;
    }
    private static void updatePlane(){plane=new EvidencePanelPlane(new Vector3d(center.x,center.y,center.z),rotation,panelWidth,panelHeight());}
    private static EvidencePanelPlane.Hit hit(){
        var client=MinecraftClient.getInstance();
        if(client.player==null||client.world!=world||!client.player.isAlive()||client.currentScreen!=null)return null;
        var eye=client.player.getEyePos();var direction=client.player.getRotationVec(1);
        var hit=plane.intersect(new Vector3d(eye.x,eye.y,eye.z),new Vector3d(direction.x,direction.y,direction.z),5);
        if(hit==null)return null;
        var end=eye.add(direction.multiply(hit.distance()));
        var block=world.raycast(new RaycastContext(eye,end,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,client.player));
        return block.getType()!=HitResult.Type.MISS&&eye.squaredDistanceTo(block.getPos())+1e-6<eye.squaredDistanceTo(end)?null:hit;
    }
    private static void render(WorldRenderContext context){
        var client=MinecraftClient.getInstance();
        if(panel==null||client.world!=world||FramebufferOverride.getOverride()!=null||PortalFramebufferOverride.get()!=null)return;
        var camera=context.camera().getPos();if(center.squaredDistanceTo(camera)>32*32)return;
        var normal=new Quaternionf(rotation).transform(new Vector3f(0,0,1));
        if(camera.subtract(center).dotProduct(new Vec3d(normal.x,normal.y,normal.z))<=0)return;
        if(reload){releaseGraphics();reload=false;}
        if(texture==null){texture=new EvidencePanelTexture(resolutionScale);allocator=new BufferAllocator(4096);consumers=VertexConsumerProvider.immediate(allocator);}
        var hit=hit();int x=hit==null?-1:(int)(hit.u()*EvidencePanelTexture.LOGICAL_WIDTH),y=hit==null?-1:(int)(hit.v()*EvidencePanelTexture.LOGICAL_HEIGHT);
        texture.render(panel,x,y,0);
        var matrices=context.matrixStack();matrices.push();
        try {
            matrices.translate(center.x-camera.x,center.y-camera.y,center.z-camera.z);matrices.multiply(rotation);
            var vertices=consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(EvidencePanelTexture.ID));
            // FBO texture V increases upward; logical UI Y increases downward.
            vertex(vertices,matrices.peek(),-panelWidth/2,panelHeight()/2,0,1);
            vertex(vertices,matrices.peek(),-panelWidth/2,-panelHeight()/2,0,0);
            vertex(vertices,matrices.peek(),panelWidth/2,-panelHeight()/2,1,0);
            vertex(vertices,matrices.peek(),panelWidth/2,panelHeight()/2,1,1);
            consumers.draw();
        } finally {matrices.pop();}
    }
    private static void vertex(VertexConsumer out,net.minecraft.client.util.math.MatrixStack.Entry matrix,float x,float y,float u,float v){
        out.vertex(matrix,x,y,0).color(255,255,255,255).texture(u,v).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(matrix,0,0,1);
    }
    private static void releaseGraphics(){if(texture!=null){texture.close();texture=null;}if(allocator!=null){allocator.close();allocator=null;consumers=null;}}
    public static void clear(){if(panel!=null)panel.dispose();panel=null;plane=null;world=null;removeRequested=false;feedbackUntil=0;releaseGraphics();}
}
