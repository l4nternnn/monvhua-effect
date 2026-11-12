package com.kuilunfuzhe.monvhua.features.forensics;

import com.kuilunfuzhe.monvhua.features.mirror.FramebufferOverride;
import com.kuilunfuzhe.monvhua.features.portal.client.PortalFramebufferOverride;
import com.mojang.brigadier.arguments.DoubleArgumentType;
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
import java.util.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

/** Server-owned private world panels. One content panel is rendered per viewer; inactive anchors keep a dark background. */
public final class EvidenceWorldPreview {
    private static final int MAX_PANELS=20;
    private static final Identifier BACKGROUND=Identifier.ofVanilla("textures/block/white_concrete.png");
    private static final List<Anchor> anchors=new ArrayList<>();
    private static ClientWorld world;
    private static final List<EvidenceAnchorStore.Anchor> saved=new ArrayList<>();
    private static long revision=-1;

    private static Anchor active;
    private static EvidencePanelTexture texture;
    private static BufferAllocator allocator;
    private static VertexConsumerProvider.Immediate consumers;
    private static volatile boolean reload;
    private static float panelWidth=1.5f;
    private static int resolutionScale=1;
    private static long feedbackUntil;
    private static String feedback="";
    private static KeyBinding interactionKey;
    private static boolean useHeld;
    private static final class Anchor {
        final boolean board; final UUID id; final ClientWorld world; final Vec3d center; final Quaternionf rotation; final float width;
        EvidencePanel panel; EvidencePanelPlane plane; long created=System.nanoTime(), lastInteraction=0;
        Anchor(ClientWorld world,EvidenceAnchorStore.Anchor data){this.board=data.board();this.id=data.id();this.world=world;this.center=new Vec3d(data.x(),data.y(),data.z());this.width=data.width();
            this.rotation=new Quaternionf().rotationYXZ((float)Math.toRadians(data.yaw()),(float)Math.toRadians(data.pitch()),(float)Math.toRadians(data.roll()));updatePlane();}
        void ensurePanel(){if(panel==null){panel=new EvidencePanel(true,()->remove(this));if(board)panel.showClueBoard();panel.resize(EvidencePanelTexture.LOGICAL_WIDTH,EvidencePanelTexture.LOGICAL_HEIGHT);}}
        void updatePlane(){plane=new EvidencePanelPlane(new Vector3d(center.x,center.y,center.z),rotation,width,width*9/16);}
        void touch(){lastInteraction=System.nanoTime();}
    }
    private static float panelHeight(){return panelWidth*9/16;}
    private static String settings(){return "面板上限 "+MAX_PANELS+"，当前维度 "+anchors.size()+"，全部维度 "+saved.size()+"；尺寸 "+panelWidth+" × "+panelHeight()+" 格，贴图 "+(EvidencePanelTexture.WIDTH*resolutionScale)+" × "+(EvidencePanelTexture.HEIGHT*resolutionScale);}

    public static void initialize(){
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(EvidenceAnchorNetwork.Snapshot.ID,(packet,context)->context.client().execute(()->{
            long next=packet.data().getLong("revision",0);if(next<revision)return;revision=next;saved.clear();
            for(var element:packet.data().getListOrEmpty("anchors"))if(element instanceof net.minecraft.nbt.NbtCompound n&&saved.size()<MAX_PANELS)try{saved.add(EvidenceAnchorStore.Anchor.read(n));}catch(IllegalArgumentException ignored){}
            reconcile();
        }));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((h,c)->{saved.clear();revision=-1;clearAll();world=null;});
        interactionKey=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.monvhua.evidence_world_interact",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_UNKNOWN,"category.monvhua"));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher,access)->dispatcher.register(literal("monvhua-evidence-preview")
            .then(literal("place").executes(ctx->placeInFront()))
            .then(literal("place_board").executes(ctx->edit("place_board","",0,0,0,0,0,0,panelWidth)))
            .then(literal("place_at").then(argument("x",DoubleArgumentType.doubleArg()).then(argument("y",DoubleArgumentType.doubleArg()).then(argument("z",DoubleArgumentType.doubleArg()).then(argument("yaw",FloatArgumentType.floatArg(-360,360)).then(argument("pitch",FloatArgumentType.floatArg(-360,360)).then(argument("roll",FloatArgumentType.floatArg(-360,360)).executes(ctx->placeAt(
                DoubleArgumentType.getDouble(ctx,"x"),DoubleArgumentType.getDouble(ctx,"y"),DoubleArgumentType.getDouble(ctx,"z"),FloatArgumentType.getFloat(ctx,"yaw"),FloatArgumentType.getFloat(ctx,"pitch"),FloatArgumentType.getFloat(ctx,"roll"))))))))))
            .then(literal("rotate").then(argument("yaw",FloatArgumentType.floatArg(-360,360))
                .then(argument("pitch",FloatArgumentType.floatArg(-360,360))
                .then(argument("roll",FloatArgumentType.floatArg(-360,360)).executes(ctx->{
                    var a=active;if(a==null)return 0;
                    return edit("rotate",a.id.toString(),0,0,0,FloatArgumentType.getFloat(ctx,"yaw"),FloatArgumentType.getFloat(ctx,"pitch"),FloatArgumentType.getFloat(ctx,"roll"),a.width);
                })))))
            .then(literal("clear_all").executes(ctx->edit("clear","",0,0,0,0,0,0,0)))
            .then(literal("remove").executes(ctx->{removeActive();return 1;}))
            .then(literal("size").then(argument("width",FloatArgumentType.floatArg(.5f,8)).executes(ctx->{float value=FloatArgumentType.getFloat(ctx,"width");if(!Float.isFinite(value))return 0;panelWidth=value;return edit("size","",0,0,0,0,0,0,value);})))
            .then(literal("resolution").then(argument("scale",IntegerArgumentType.integer(1,4)).executes(ctx->{resolutionScale=IntegerArgumentType.getInteger(ctx,"scale");releaseGraphics();ctx.getSource().sendFeedback(Text.literal(settings()));return 1;})))
            .then(literal("status").executes(ctx->{ctx.getSource().sendFeedback(Text.literal(settings()));return 1;}))));
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(!client.options.useKey.isPressed())useHeld=false;
            if(client.world!=world){clearAll();world=client.world;reconcile();}
            if(client.player==null||client.currentScreen!=null)return;
            if(anchors.isEmpty()&&!saved.isEmpty())reconcile();
            active=chooseActive(client);
            while(interactionKey.wasPressed())interactAimed();
            if(client.world==null)EvidenceSessionRouter.clear();
        });
        HudRenderCallback.EVENT.register((draw,tick)->{
            var client=MinecraftClient.getInstance();if(client.currentScreen!=null||client.options.hudHidden)return;var aimed=aimed();if(aimed==null)return;var hit=hit(aimed);
            String label=aimed!=active||aimed.panel==null?"激活此面板":aimed.panel.worldHoverLabel(hit.u()*EvidencePanelTexture.LOGICAL_WIDTH,hit.v()*EvidencePanelTexture.LOGICAL_HEIGHT);
            String hint=System.nanoTime()<feedbackUntil?feedback:label.isEmpty()?"已瞄准面板 · 请对准按钮、轮盘或卡片":"右键（"+client.options.useKey.getBoundKeyLocalizedText().getString()+"）："+label;
            int cx=draw.getScaledWindowWidth()/2,y=draw.getScaledWindowHeight()/2+18,half=client.textRenderer.getWidth(hint)/2;draw.fill(cx-half-5,y-3,cx+half+5,y+12,0xC0101820);draw.drawCenteredTextWithShadow(client.textRenderer,hint,cx,y,label.isEmpty()?0xFFCBD5DF:0xFFFFD65A);
        });
        WorldRenderEvents.AFTER_ENTITIES.register(EvidenceWorldPreview::render);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client->clearAll());
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener(){public Identifier getFabricId(){return Identifier.of("monvhua","evidence_world_preview");}public void reload(ResourceManager manager){reload=true;}});
    }
    /** Consume the whole press so holding use cannot confirm a publication or use an item behind the UI. */
    public static boolean usePanel(){
        var client=MinecraftClient.getInstance();
        if(client.player==null||client.currentScreen!=null)return false;
        if(useHeld)return true;
        if(!interactAimed())return false;
        useHeld=true;return true;
    }
    private static boolean interactAimed(){
        var aimed=aimed();if(aimed==null)return false;
        if(aimed!=active||aimed.panel==null){active=aimed;active.touch();active.ensurePanel();feedback="已激活面板";feedbackUntil=System.nanoTime()+900_000_000L;return true;}
        var hit=hit(active);if(hit==null)return false;
        active.touch();double x=hit.u()*EvidencePanelTexture.LOGICAL_WIDTH,y=hit.v()*EvidencePanelTexture.LOGICAL_HEIGHT;
        String label=active.panel.worldHoverLabel(x,y);boolean handled=active.panel.worldClick(x,y);
        feedback=handled?"已点击："+label:"此处没有可操作控件，请对准按钮、轮盘或卡片";feedbackUntil=System.nanoTime()+900_000_000L;return true;
    }
    private static int edit(String action,String id,double x,double y,double z,float yaw,float pitch,float roll,float width){
        if(!net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(EvidenceAnchorNetwork.Edit.ID))return 0;
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new EvidenceAnchorNetwork.Edit(action,id,x,y,z,yaw,pitch,roll,width));return 1;
    }
    private static int placeInFront(){return edit("place","",0,0,0,0,0,0,panelWidth);}
    private static int placeAt(double x,double y,double z,float yaw,float pitch,float roll){return edit("place_at","",x,y,z,yaw,pitch,roll,panelWidth);}
    private static void reconcile(){
        var c=MinecraftClient.getInstance();if(c.world==null||c.player==null)return;
        var dim=c.world.getRegistryKey().getValue().toString();var keep=new HashSet<UUID>();
        for(var data:saved){if(!data.owner().equals(c.player.getUuid())||!data.dimension().equals(dim))continue;keep.add(data.id());
            var old=anchors.stream().filter(a->a.id.equals(data.id())).findFirst().orElse(null);
            var next=new Anchor(c.world,data);
            if(old!=null&&old.center.equals(next.center)&&old.rotation.equals(next.rotation)&&old.width==next.width&&old.board==next.board)continue;
            if(old!=null){next.panel=old.panel;next.created=old.created;next.lastInteraction=old.lastInteraction;anchors.remove(old);if(active==old)active=next;}
            anchors.add(next);
        }
        for(var a:List.copyOf(anchors))if(!keep.contains(a.id)){anchors.remove(a);if(a.panel!=null)a.panel.dispose();if(active==a)active=null;}
    }
    private static Anchor chooseActive(MinecraftClient c){if(c.player==null||anchors.isEmpty())return null;var eye=c.player.getEyePos();Anchor recent=anchors.stream().filter(a->a.world==c.world&&eye.squaredDistanceTo(a.center)<=32*32).max(Comparator.comparingLong(a->a.lastInteraction)).orElse(null);if(recent!=null&&System.nanoTime()-recent.lastInteraction<3_000_000_000L)return recent;return anchors.stream().filter(a->a.world==c.world&&eye.squaredDistanceTo(a.center)<=32*32).min(Comparator.comparingDouble(a->eye.squaredDistanceTo(a.center))).orElse(null);}
    private static Anchor aimed(){return anchors.stream().filter(a->animation(a)>=1).filter(a->hit(a)!=null).min(Comparator.comparingDouble(a->hit(a).distance())).orElse(null);}
    private static EvidencePanelPlane.Hit hit(Anchor a){var c=MinecraftClient.getInstance();if(a==null||c.player==null||c.world!=a.world||!c.player.isAlive()||c.currentScreen!=null)return null;var eye=c.player.getEyePos();var dir=c.player.getRotationVec(1);var h=a.plane.intersect(new Vector3d(eye.x,eye.y,eye.z),new Vector3d(dir.x,dir.y,dir.z),5);if(h==null)return null;var end=eye.add(dir.multiply(h.distance()));var block=c.world.raycast(new RaycastContext(eye,end,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,c.player));return block.getType()!=HitResult.Type.MISS&&eye.squaredDistanceTo(block.getPos())+1e-6<eye.squaredDistanceTo(end)?null:h;}
    private static void render(WorldRenderContext context){
        var c=MinecraftClient.getInstance();if(anchors.isEmpty()||c.world==null||FramebufferOverride.getOverride()!=null||PortalFramebufferOverride.get()!=null)return;active=chooseActive(c);var camera=context.camera().getPos();if(reload){releaseGraphics();reload=false;}
        if(allocator==null){allocator=new BufferAllocator(4096);consumers=VertexConsumerProvider.immediate(allocator);}
        if(active!=null&&camera.squaredDistanceTo(active.center)<=32*32&&camera.subtract(active.center).dotProduct(new Vec3d(new Quaternionf(active.rotation).transform(new Vector3f(0,0,1)).x,new Quaternionf(active.rotation).transform(new Vector3f(0,0,1)).y,new Quaternionf(active.rotation).transform(new Vector3f(0,0,1)).z))>0){active.ensurePanel();if(texture==null)texture=new EvidencePanelTexture(resolutionScale);var h=hit(active);int mx=h==null?-1:(int)(h.u()*EvidencePanelTexture.LOGICAL_WIDTH),my=h==null?-1:(int)(h.v()*EvidencePanelTexture.LOGICAL_HEIGHT);texture.render(active.panel,mx,my,0);drawQuad(context,active,true,animation(active));}
        for(var a:anchors)if(a!=active&&a.world==c.world&&camera.squaredDistanceTo(a.center)<=32*32)drawQuad(context,a,false,animation(a));
    }
    private static float animation(Anchor a){return Math.min(1f,(System.nanoTime()-a.created)/600_000_000f);}
    private static void drawQuad(WorldRenderContext context,Anchor a,boolean content,float progress){var camera=context.camera().getPos();var m=context.matrixStack();m.push();try{m.translate(a.center.x-camera.x,a.center.y-camera.y,a.center.z-camera.z);m.multiply(a.rotation);float horizontal=Math.min(1,progress/.35f),vertical=progress<.35f?0:Math.min(1,(progress-.35f)/.65f);float w=a.width*horizontal,h=Math.max(.008f,a.width*9/16*vertical);content=content&&progress>=1;var vc=consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(content?EvidencePanelTexture.ID:BACKGROUND));vertex(vc,m.peek(),-w/2,h/2,0,1,content);vertex(vc,m.peek(),-w/2,-h/2,0,0,content);vertex(vc,m.peek(),w/2,-h/2,1,0,content);vertex(vc,m.peek(),w/2,h/2,1,1,content);consumers.draw();}finally{m.pop();}}
    private static void vertex(VertexConsumer out,net.minecraft.client.util.math.MatrixStack.Entry matrix,float x,float y,float u,float v,boolean content){out.vertex(matrix,x,y,0).color(content?255:18,content?255:24,content?255:30,255).texture(u,v).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(matrix,0,0,1);}
    private static void remove(Anchor a){edit("remove",a.id.toString(),0,0,0,0,0,0,0);}
    private static void removeActive(){var target=aimed();if(target==null)target=active;if(target!=null)remove(target);}
    private static void clearAll(){useHeld=false;for(var a:anchors)if(a.panel!=null)a.panel.dispose();anchors.clear();active=null;releaseGraphics();}
    private static void releaseGraphics(){if(texture!=null){texture.close();texture=null;}if(allocator!=null){allocator.close();allocator=null;consumers=null;}}
}
