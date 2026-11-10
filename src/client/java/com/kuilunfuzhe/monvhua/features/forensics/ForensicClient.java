package com.kuilunfuzhe.monvhua.features.forensics;

import com.kuilunfuzhe.monvhua.network.forensics.ForensicPackets;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.entity.decoration.InteractionEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import com.kuilunfuzhe.monvhua.network.forensics.ForensicSharePackets;
import org.lwjgl.glfw.GLFW;
import java.util.*;

public final class ForensicClient {
    private static int sequence;
    private static int openRequest = -1;
    private static int openTarget = -1;
    public static void initialize() {
        EvidenceWorldPreview.initialize();
        com.kuilunfuzhe.monvhua.features.forensics.blood.BloodTraceClient.initialize();
        KeyBinding inspect = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.monvhua.forensic_inspect", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "category.monvhua"));
        KeyBinding mode = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.monvhua.forensic_mode", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "category.monvhua"));
        KeyBinding summary = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.monvhua.forensic_summary", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "category.monvhua"));
        // Same gesture as carrying a living entity: sneak + empty hands + use entity.
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient && hand == Hand.MAIN_HAND && player.isSneaking()
                    && player.getMainHandStack().isEmpty() && player.getOffHandStack().isEmpty()
                    && requestOpen(entity)) return ActionResult.SUCCESS;
            return ActionResult.PASS;
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (summary.wasPressed()) {
                if (client.player != null && client.currentScreen == null && ClientPlayNetworking.canSend(ForensicPackets.SummaryRequest.ID))
                    client.setScreen(new EvidenceSummaryScreen());
            }
            while (mode.wasPressed()) if (client.player != null && client.currentScreen == null && ClientPlayNetworking.canSend(ForensicPackets.Request.ID))
                ClientPlayNetworking.send(new ForensicPackets.Request(-1, "", true, ++sequence));
            while (inspect.wasPressed()) {
                if (client.player != null && client.currentScreen == null && ClientPlayNetworking.canSend(ForensicPackets.Request.ID)) {
                    if (client.player.isSneaking()) {
                        ClientPlayNetworking.send(new ForensicPackets.Request(-1, "__stains", false, ++sequence)); continue;
                    }
                    if (client.crosshairTarget instanceof EntityHitResult target && target.getEntity() instanceof net.minecraft.entity.player.PlayerEntity) {
                        ClientPlayNetworking.send(new ForensicPackets.Request(target.getEntity().getId(), "__stains", false, ++sequence)); continue;
                    }
                }
                if (client.crosshairTarget instanceof EntityHitResult hit && requestOpen(hit.getEntity())) continue;
                if(client.player!=null&&client.currentScreen==null&&ClientPlayNetworking.canSend(ForensicPackets.Request.ID)){
                    openTarget=-1;openRequest=++sequence;ClientPlayNetworking.send(new ForensicPackets.Request(-1,"__open",false,openRequest));
                }
                if (client.player != null && client.currentScreen == null && ClientPlayNetworking.canSend(com.kuilunfuzhe.monvhua.network.forensics.BloodTracePackets.Inspect.ID))
                    ClientPlayNetworking.send(new com.kuilunfuzhe.monvhua.network.forensics.BloodTracePackets.Inspect());
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(ForensicSharePackets.Notice.ID, (packet, context) -> context.client().execute(() -> {
            if(context.client().player!=null)context.client().player.sendMessage(Text.literal("我已收录他人分享的线索"),false);
            EvidenceSessionRouter.shared(packet.corpse(),packet.part(),packet.evidenceId());
        }));
        ClientPlayNetworking.registerGlobalReceiver(ForensicPackets.SummaryResult.ID, (packet, context) -> context.client().execute(() -> EvidenceSessionRouter.receive(packet)));
        ClientPlayNetworking.registerGlobalReceiver(ForensicPackets.Result.ID, (packet, context) -> context.client().execute(() -> {
            var client = context.client();
            if (packet.part().equals("__stains")) {
                if (client.player != null) for (String line : packet.lines()) client.player.sendMessage(Text.literal(line), false);
                return;
            }
            if (packet.part().equals("__open")) {
                if(packet.requestId()==openRequest&&packet.target()==openTarget&&client.currentScreen==null){
                    if(packet.lines().equals(List.of("release")))client.setScreen(new InspectionScreen(openTarget,"",true,false));
                    else if(packet.lines().size()==3&&packet.lines().getFirst().equals("ready")
                            &&client.crosshairTarget instanceof EntityHitResult hit&&hit.getEntity().getId()==openTarget)
                        client.setScreen(new InspectionScreen(openTarget,packet.lines().get(1),false,packet.lines().get(2).equals("blocked")));
                    else if(client.player!=null&&!packet.lines().isEmpty())client.player.sendMessage(Text.literal(packet.lines().getFirst()),true);
                }
                return;
            }
            if (client.currentScreen instanceof InspectionScreen screen && screen.target == packet.target()
                    && screen.pending == packet.requestId() && screen.part.equals(packet.part())) {
                screen.lines = packet.lines().isEmpty() ? List.of("无法检查：请靠近尸体并保持视线无遮挡") : packet.lines();
                screen.waiting=false;
                if(client.player!=null)client.player.sendMessage(Text.literal("探查结果："+String.join("；",screen.lines)),false);
                screen.close();
            }
        }));
    }
    private static boolean requestOpen(Entity target) {
        var client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.currentScreen != null || !ClientPlayNetworking.canSend(ForensicPackets.Request.ID)) return false;
        boolean body = target instanceof ItemDisplayEntity display && Forensics.hasBinding(display.getItemStack());
        if (target instanceof InteractionEntity) {
            // Links themselves are server-only. This is only a client hint; __open is validated server-side.
            body = !client.world.getEntitiesByClass(ItemDisplayEntity.class, target.getBoundingBox().expand(.6, 1.6, .6),
                    display -> Forensics.hasBinding(display.getItemStack())).isEmpty();
        }
        if (!body) return false;
        openTarget = target.getId(); openRequest = ++sequence;
        ClientPlayNetworking.send(new ForensicPackets.Request(openTarget, "__open", false, openRequest));
        return true;
    }
    private static final class InspectionScreen extends Screen {
        final int target;final String part;final boolean releaseOnly,blocked;
        int pending;boolean waiting;List<String> lines=List.of();
        InspectionScreen(int target,String part,boolean releaseOnly,boolean blocked){super(Text.literal("遗体检查"));this.target=target;this.part=part;this.releaseOnly=releaseOnly;this.blocked=blocked;}
        private int radius(){return Math.max(32,Math.min(52,height/5));}
        private int cx(){return Math.max(radius()+8,width/2-radius()-18);}
        private int cy(){return height/2;}
        @Override public boolean shouldPause(){return false;}
        @Override public void renderBackground(DrawContext c,int x,int y,float delta){}
        private int sector(double x,double y){return InspectionWheelGeometry.sector(x-cx(),y-cy(),radius(),part.equals("torso"),releaseOnly);}
        @Override public boolean mouseClicked(double x,double y,int button){
            if(button==1){close();return true;}
            if(button!=0)return super.mouseClicked(x,y,button);
            int slot=sector(x,y);if(slot<0)return true;
            if(releaseOnly){
                if(ClientPlayNetworking.canSend(ForensicPackets.CarryRequest.ID))ClientPlayNetworking.send(new ForensicPackets.CarryRequest(-1,true));close();return true;
            }
            if(slot!=0){
                if((slot==1&&blocked)||part.equals("head"))return true;
                if(ClientPlayNetworking.canSend(ForensicPackets.CarryRequest.ID))ClientPlayNetworking.send(new ForensicPackets.CarryRequest(target,false,slot==2?"carry":"drag",part));
                close();return true;
            }
            if(!waiting){waiting=true;pending=++sequence;ClientPlayNetworking.send(new ForensicPackets.Request(target,part,false,pending));}
            return true;
        }
        @Override public void render(DrawContext c,int mx,int my,float delta){
            int r=radius(),hover=sector(mx,my);
            for(int y=-r;y<=r;y++){
                int start=-r,previous=-1;
                for(int x=-r;x<=r+1;x++){
                    int slot=x>r?-1:sector(cx()+x,cy()+y);
                    if(slot!=previous){
                        if(previous>=0)c.fill(cx()+start,cy()+y,cx()+x,cy()+y+1,
                            previous==0?(hover==previous?0xDD786348:0xCC293743):(hover==previous?0xCC4D5056:0xBB30343C));
                        start=x;previous=slot;
                    }
                }
            }
            if(releaseOnly)c.drawCenteredTextWithShadow(textRenderer,Text.literal("放下遗体"),cx(),cy()+r/2,0xFFFFD49B);
            else {
                c.drawCenteredTextWithShadow(textRenderer,Text.literal(waiting?"检查中…":"检查"+label(part)),cx(),cy()+r/2,0xFFF1E7D6);
                if(part.equals("torso")){
                    c.drawCenteredTextWithShadow(textRenderer,Text.literal("拖拽"),cx()-r/3,cy()-r*2/3,0xFF939BA6);
                    c.drawCenteredTextWithShadow(textRenderer,Text.literal("抱起"),cx()+r/3,cy()-r*2/3,0xFF939BA6);
                }else c.drawCenteredTextWithShadow(textRenderer,Text.literal("拖拽"),cx(),cy()-r*3/4,0xFF939BA6);
                if(hover>0)c.drawCenteredTextWithShadow(textRenderer,Text.literal(hover==1&&blocked?"当前持有状态禁止拖拽":part.equals("head")?"请瞄准四肢拖拽":hover==2?"承托躯干，四肢自然摆动":"拖动所选肢体；躯干自动选择最近四肢"),cx(),cy()+r+7,0xFFBDC6CF);
            }
            // No full-screen background, blur, title or footer buttons.
        }
        private String label(String value){return switch(value){case "head"->"头部";case "torso"->"躯干";case "left_arm"->"左臂";case "right_arm"->"右臂";case "left_leg"->"左腿";case "right_leg"->"右腿";default->"部位";};}
    }
}
