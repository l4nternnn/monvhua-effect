package com.kuilunfuzhe.monvhua.features.physics_preview;

import com.kuilunfuzhe.monvhua.features.forensics.physics.*;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.render.*;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import java.util.UUID;
import java.util.function.Consumer;

/** Captures physical input only while the dedicated grabber is held. */
public final class CorpseGrabClient {
    private static KeyBinding freeze,range,toStatic;
    private static boolean staticDown;
    private static boolean left,right,partial,pending;
    private static final java.util.concurrent.atomic.AtomicBoolean inputPending=new java.util.concurrent.atomic.AtomicBoolean();
    private static double scroll,dx,dy;
    private static UUID lastPlayer;private static net.minecraft.server.MinecraftServer lastServer;
    public static void initialize(){
        freeze=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.monvhua.grab_freeze",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_F,"category.monvhua"));
        range=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.monvhua.grab_range",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_V,"category.monvhua"));
        toStatic=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.monvhua.grab_static",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_H,"category.monvhua"));
        HudRenderCallback.EVENT.register((draw,tick)->{
            if(!holding())return;var c=MinecraftClient.getInstance();var s=RemoteCorpsePhysics.status(c.player.getUuid());
            String text=s==null?"物理抓取器 · "+(partial?"单肢体":"整体")+" · 右键抓取": "抓取 "+partName(s.part())+" · "+(s.local()?"单肢体":"整体")+" · "+String.format(java.util.Locale.ROOT,"%.2f 格",s.distance())+(s.frozen()?" · 已冻结":"");
            if(s==null){var hit=CorpseGrabController.pick(c.player);var e=hit==null?null:CorpseHistoryController.get(hit.id());
                if(e!=null&&e.session.residentState()!=null&&e.session.residentState().phase().equals("PINNED"))text+=" · 肢体槽位空闲，右键继续调姿 / 双击 H 转静态";
            }
            draw.drawTextWithShadow(c.textRenderer,text,12,12,0xff70e7ff);
            draw.drawTextWithShadow(c.textRenderer,"滚轮调距 · 按住左键旋转 · "+freeze.getBoundKeyLocalizedText().getString()+" "+((s==null?partial:s.local())?"固定肢体并释放槽位":"全冻结")+" · "+range.getBoundKeyLocalizedText().getString()+" 切换范围 · 双击 "+toStatic.getBoundKeyLocalizedText().getString()+" 转静态",12,26,0xffffffff);
        });
        WorldRenderEvents.AFTER_ENTITIES.register(CorpseGrabClient::render);
    }
    public static boolean holding(){var c=MinecraftClient.getInstance();return c.player!=null&&c.player.getMainHandStack().isOf(CorpseGrabController.ITEM);}
    public static boolean inputAllowed(){var c=MinecraftClient.getInstance();return holding()&&c.currentScreen==null&&c.isWindowFocused()&&!c.isPaused();}
    public static boolean button(int button,int action){
        if(!inputAllowed())return false;
        if(button==GLFW.GLFW_MOUSE_BUTTON_LEFT){left=action!=GLFW.GLFW_RELEASE;return true;}
        if(button==GLFW.GLFW_MOUSE_BUTTON_RIGHT){
            if(action==GLFW.GLFW_PRESS&&!right){right=true;boolean mode=partial;request("toggle",mode,p->CorpseGrabController.toggle(p,mode));}
            if(action==GLFW.GLFW_RELEASE)right=false;return true;
        }return false;
    }
    public static boolean scroll(double amount){var c=MinecraftClient.getInstance();if(!inputAllowed()||RemoteCorpsePhysics.status(c.player.getUuid())==null)return false;scroll=Math.clamp(scroll+amount,-8,8);return true;}
    public static boolean look(double x,double y){
        var c=MinecraftClient.getInstance();if(!inputAllowed()||!left||RemoteCorpsePhysics.status(c.player.getUuid())==null)return false;
        dx=Math.clamp(dx+x,-500,500);dy=Math.clamp(dy+y,-500,500);return true;
    }
    public static boolean key(int key,int scan,int action){
        if(!inputAllowed())return false;
        if(toStatic.matchesKey(key,scan)){
            if(action==GLFW.GLFW_RELEASE)staticDown=false;
            else if(action==GLFW.GLFW_PRESS&&!staticDown){staticDown=true;request("static",partial,CorpseGrabController::staticPress);}
            return true;
        }
        boolean f=freeze.matchesKey(key,scan),r=range.matchesKey(key,scan);if(!f&&!r)return false;
        if(action==GLFW.GLFW_PRESS){if(f){boolean mode=partial;request("freeze",mode,p->CorpseGrabController.freeze(p,mode));}else{partial=!partial;request("range",partial,CorpseGrabController::range);}}return true;
    }
    private static void request(String action,boolean partial,Consumer<net.minecraft.server.network.ServerPlayerEntity> operation){
        var c=MinecraftClient.getInstance();var server=c.getServer();if(c.player==null)return;if(server==null){RemoteCorpsePhysics.action(action,partial,0,Vec3d.ZERO,false);return;}if(pending)return;pending=true;var id=c.player.getUuid();
        server.execute(()->{String error=null;try{var p=server.getPlayerManager().getPlayer(id);if(p!=null)operation.accept(p);}catch(RuntimeException ex){error=ex.getMessage();}var result=error;c.execute(()->{pending=false;if(result!=null&&c.getServer()==server)message(result);});});
    }
    public static void tick(MinecraftClient c){
        if(!inputAllowed()){
            left=false;right=false;staticDown=false;scroll=dx=dy=0;
            if(lastServer==null&&lastPlayer!=null)RemoteCorpsePhysics.action("release",partial,0,Vec3d.ZERO,false);
            if(lastServer!=null&&lastPlayer!=null){var id=lastPlayer;lastServer.execute(()->CorpseGrabController.release(id));}lastPlayer=null;lastServer=null;return;
        }
        var server=c.getServer();lastServer=server;lastPlayer=c.player.getUuid();
        if(c.world.getTickManager().isFrozen()){scroll=dx=dy=0;return;}
        var s=RemoteCorpsePhysics.status(lastPlayer);if(s==null){scroll=dx=dy=0;return;}
        // Entity look deltas already include Minecraft sensitivity. Convert to radians per 50 ms tick.
        double yaw=Math.toRadians(c.player.getYaw());var rightAxis=new Vec3d(-Math.cos(yaw),0,-Math.sin(yaw));
        var up=rightAxis.crossProduct(c.player.getRotationVec(1)).normalize();
        var angular=up.multiply(-dx*.002618/0.05).add(rightAxis.multiply(-dy*.002618/0.05));
        if(angular.length()>2)angular=angular.normalize().multiply(2);
        if(server==null){RemoteCorpsePhysics.action("input",partial,scroll,angular,left);scroll=dx=dy=0;return;}
        if(!inputPending.compareAndSet(false,true))return;
        var a=angular;double wheel=scroll;boolean rotating=left;var id=lastPlayer;scroll=dx=dy=0;
        server.execute(()->{try{var p=server.getPlayerManager().getPlayer(id);if(p!=null)CorpseGrabController.input(p,wheel,a,rotating);}catch(RuntimeException ignored){CorpseGrabController.release(id);}finally{inputPending.set(false);}});
    }
    private static String partName(String id){return switch(id){case "head"->"头部";case "torso"->"躯干";case "left_arm"->"左臂";case "right_arm"->"右臂";case "left_leg"->"左腿";case "right_leg"->"右腿";default->id;};}
    private static void message(String text){var p=MinecraftClient.getInstance().player;if(p!=null)p.sendMessage(Text.literal(text),false);}
    private static void render(WorldRenderContext context){
        if(RemoteCorpsePhysics.remote())return;
        if(!holding()||context.matrixStack()==null||context.consumers()==null)return;
        var c=MinecraftClient.getInstance();var s=RemoteCorpsePhysics.status(c.player.getUuid());
        var hit=s==null?CorpseGrabController.pick(c.player):null;
        var id=s==null?(hit==null?null:hit.id()):s.id();if(id==null)return;
        String part=s==null?hit.part():s.part();var entry=CorpseHistoryController.get(id);if(entry==null||entry.view==null)return;
        var frames=entry.session.frames();if(frames.isEmpty())return;var pose=frames.getLast().poses().get(part);
        var matrices=context.matrixStack();var camera=context.camera().getPos();var vertices=context.consumers().getBuffer(RenderLayer.getLines());
        matrices.push();try{
            var origin=entry.view.origin().subtract(camera);matrices.translate(origin.x+pose.x(),origin.y+pose.y(),origin.z+pose.z());matrices.multiply(pose.rotation());
            var size=StandardBodyPhysicsAdapter.size(part,entry.view.slim()).mul(.5f).add(.006f,.006f,.006f);
            VertexRendering.drawBox(matrices,vertices,-size.x,-size.y,-size.z,size.x,size.y,size.z,0,1,1,1);
        }finally{matrices.pop();}
        if(s!=null){
            for(var point:java.util.List.of(s.actual(),s.target())){var p=point.subtract(camera);VertexRendering.drawBox(matrices,vertices,p.x-.025,p.y-.025,p.z-.025,p.x+.025,p.y+.025,p.z+.025,1,.7f,0,1);}
            var a=s.actual().subtract(camera);var b=s.target().subtract(camera);var n=b.subtract(a).normalize();
            if(n.lengthSquared()>0){var m=matrices.peek();vertices.vertex(m,(float)a.x,(float)a.y,(float)a.z).color(255,180,0,255).normal(m,(float)n.x,(float)n.y,(float)n.z);vertices.vertex(m,(float)b.x,(float)b.y,(float)b.z).color(255,180,0,255).normal(m,(float)n.x,(float)n.y,(float)n.z);}
        }
    }
}
