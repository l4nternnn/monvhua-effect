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
import org.lwjgl.glfw.GLFW;
import java.util.*;

public final class ForensicClient {
    private static int sequence;
    private static int openRequest = -1;
    private static int openTarget = -1;
    public static void initialize() {
        com.kuilunfuzhe.monvhua.features.forensics.blood.BloodTraceClient.initialize();
        KeyBinding inspect = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.monvhua.forensic_inspect", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "category.monvhua"));
        KeyBinding mode = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.monvhua.forensic_mode", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "category.monvhua"));
        KeyBinding release = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.monvhua.forensic_release", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "category.monvhua"));
        // Same gesture as carrying a living entity: sneak + empty hands + use entity.
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient && hand == Hand.MAIN_HAND && player.isSneaking()
                    && player.getMainHandStack().isEmpty() && player.getOffHandStack().isEmpty()
                    && requestOpen(entity)) return ActionResult.SUCCESS;
            return ActionResult.PASS;
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (release.wasPressed()) if (client.player != null && client.currentScreen == null && ClientPlayNetworking.canSend(ForensicPackets.CarryRequest.ID))
                ClientPlayNetworking.send(new ForensicPackets.CarryRequest(-1, true));
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
                if (client.player != null && client.currentScreen == null && ClientPlayNetworking.canSend(com.kuilunfuzhe.monvhua.network.forensics.BloodTracePackets.Inspect.ID))
                    ClientPlayNetworking.send(new com.kuilunfuzhe.monvhua.network.forensics.BloodTracePackets.Inspect());
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(ForensicPackets.Result.ID, (packet, context) -> context.client().execute(() -> {
            var client = context.client();
            if (packet.part().equals("__stains")) {
                if (client.player != null) for (String line : packet.lines()) client.player.sendMessage(Text.literal(line), false);
                return;
            }
            if (packet.part().equals("__open")) {
                if (packet.requestId() == openRequest && packet.target() == openTarget && client.currentScreen == null
                        && client.crosshairTarget instanceof EntityHitResult hit && hit.getEntity().getId() == openTarget
                        && packet.lines().equals(List.of("ready"))) client.setScreen(new InspectionScreen(openTarget));
                return;
            }
            if (client.currentScreen instanceof InspectionScreen screen && screen.target == packet.target()
                    && screen.pending == packet.requestId() && screen.part.equals(packet.part())) {
                screen.lines = packet.lines().isEmpty() ? List.of("无法检查：请靠近尸体并保持视线无遮挡") : packet.lines();
                screen.scroll = 0;
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
        private static final String[] PARTS = {"carry", "head", "right_arm", "right_leg", "left_leg", "left_arm", "torso"};
        private static final String[] LABELS = {"抱起遗体", "头部", "右臂", "右腿", "左腿", "左臂", "躯干"};
        final int target;
        int pending;
        String part = "";
        List<String> lines = List.of();
        boolean detail;
        int scroll;
        int maxScroll;
        ButtonWidget back;
        InspectionScreen(int target) { super(Text.literal("遗体检查")); this.target = target; }
        @Override protected void init() {
            back = addDrawableChild(ButtonWidget.builder(Text.literal("返回轮盘"), button -> { detail = false; back.visible = false; })
                    .dimensions(16, height - 30, 80, 20).build());
            back.visible = detail;
            addDrawableChild(ButtonWidget.builder(Text.literal("放下遗体"), button -> {
                if (ClientPlayNetworking.canSend(ForensicPackets.CarryRequest.ID)) ClientPlayNetworking.send(new ForensicPackets.CarryRequest(-1, true));
                close();
            }).dimensions(width / 2 - 40, height - 30, 80, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("关闭"), button -> close()).dimensions(width - 80, height - 30, 65, 20).build());
        }
        private int radius() { return Math.max(40, Math.min(110, Math.min(width / 2 - 18, (height - 90) / 2))); }
        private int centerY() { return 38 + radius(); }
        @Override public boolean shouldPause() { return false; }
        @Override public boolean mouseClicked(double x, double y, int button) {
            if (!detail && button == 0) {
                int sector = CorpseWheelGeometry.sector(x - width / 2.0, y - centerY(), radius() * .35, radius());
                if (sector > 0) {
                    part = PARTS[sector]; detail = true; back.visible = true; scroll = 0;
                    lines = List.of("正在检查……"); pending = ++sequence;
                    ClientPlayNetworking.send(new ForensicPackets.Request(target, part, false, pending));
                    return true;
                }
                if (sector == 0) {
                    if (ClientPlayNetworking.canSend(ForensicPackets.CarryRequest.ID))
                        ClientPlayNetworking.send(new ForensicPackets.CarryRequest(target, false));
                    close();
                    return true;
                }
            }
            return super.mouseClicked(x, y, button);
        }
        @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
            if (detail) { scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (vertical * 24))); return true; }
            return super.mouseScrolled(x, y, horizontal, vertical);
        }
        @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(0, 0, width, height, 0xC010141D);
            context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFF1E7D6);
            if (detail) drawDetails(context);
            else drawWheel(context, mouseX, mouseY);
            super.render(context, mouseX, mouseY, delta);
        }
        private void drawWheel(DrawContext context, int mx, int my) {
            int r = radius(), cx = width / 2, cy = centerY();
            int hover = CorpseWheelGeometry.sector(mx - cx, my - cy, r * .35, r);
            // Scanline spans render the actual annular sectors, using the same hit-test geometry.
            for (int y = -r; y <= r; y++) {
                int start = -r, previous = -2;
                for (int x = -r; x <= r + 1; x++) {
                    int sector = x > r ? -2 : CorpseWheelGeometry.sector(x, y, r * .35, r);
                    if (sector != previous) {
                        if (previous >= 0) {
                            int color = previous == hover ? 0xEE8A6744 : (previous % 2 == 0 ? 0xE02B3545 : 0xE0232B39);
                            context.fill(cx + start, cy + y, cx + x, cy + y + 1, color);
                        }
                        start = x; previous = sector;
                    }
                }
            }
            for (int i = 0; i < LABELS.length; i++) {
                double angle = -Math.PI / 2 + i * CorpseWheelGeometry.STEP;
                int x = cx + (int) (Math.cos(angle) * r * .70), y = cy + (int) (Math.sin(angle) * r * .70);
                context.drawCenteredTextWithShadow(textRenderer, Text.literal(LABELS[i]), x, y - 5, i == 0 ? 0xFF8E96A4 : 0xFFF1E7D6);
            }
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("检查"), cx, cy - 4, 0xFFE6C18A);
            String hint = hover == 0 ? "抱起遗体 · 保持空手并确认安全位置" : "选择部位 · 最多查看三条最高伤害记录";
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(hint), cx, height - 48, 0xFFC4CBD6);
        }
        private void drawDetails(DrawContext context) {
            String label = LABELS[Arrays.asList(PARTS).indexOf(part)];
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(label + " · 按伤害由高到低"), width / 2, 32, 0xFFE6C18A);
            int available = Math.max(40, width - 48), y = 58 - scroll;
            context.enableScissor(16, 52, width - 16, height - 38);
            for (String line : lines) {
                var wrapped = textRenderer.wrapLines(Text.literal(line), available - 20);
                int cardHeight = wrapped.size() * 12 + 20;
                context.fill(16, y, width - 16, y + cardHeight - 6, 0xE0263040);
                for (int i = 0; i < wrapped.size(); i++) context.drawTextWithShadow(textRenderer, wrapped.get(i), 26, y + 7 + i * 12, 0xFFE2E7EE);
                y += cardHeight;
            }
            context.disableScissor();
            maxScroll = Math.max(0, y + scroll - (height - 38));
            scroll = Math.min(scroll, maxScroll);
        }
    }
}
