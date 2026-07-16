package com.kuilunfuzhe.monvhua.features.evil_eyes;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

public final class ClairvoyanceGazeAlertClient {
    public static final int TYPE_PLAYER = 0;
    public static final int TYPE_ANCHOR = 1;

    private static int alertType = TYPE_PLAYER;
    private static String watcherName = "";
    private static int watcherCount = 0;
    private static long expiresAtTick = 0L;

    private ClairvoyanceGazeAlertClient() {
    }

    public static void receive(int type, String name, int count, int durationTicks) {
        MinecraftClient client = MinecraftClient.getInstance();
        long now = client.world != null ? client.world.getTime() : System.currentTimeMillis() / 50L;
        alertType = type;
        watcherName = name == null || name.isBlank() ? "\u672a\u77e5" : name;
        watcherCount = Math.max(1, count);
        expiresAtTick = now + Math.max(1, durationTicks);
    }

    public static boolean hasActiveAlert() {
        MinecraftClient client = MinecraftClient.getInstance();
        long now = client.world != null ? client.world.getTime() : System.currentTimeMillis() / 50L;
        return watcherCount > 0 && now < expiresAtTick;
    }

    public static boolean hasClairvoyanceInInventory(PlayerEntity player) {
        if (player == null) {
            return false;
        }
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == Evil_Eyes.CLAIRVOYANCE_ITEM) {
                return true;
            }
        }
        return false;
    }

    public static void renderIfVisible(DrawContext context, int x, int y) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || !hasActiveAlert() || !hasClairvoyanceInInventory(client.player)) {
            return;
        }
        render(context, x, y);
    }

    public static void render(DrawContext context, int x, int y) {
        if (!hasActiveAlert()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        String message = message();
        int width = client.textRenderer.getWidth(message) + 12;
        int height = 13;
        context.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xAA2A0710);
        context.fill(x, y, x + width, y + height, 0xCC14070D);
        context.fill(x, y + height - 1, x + width, y + height, 0xFFC00000);
        context.drawTextWithShadow(client.textRenderer, message, x + 6, y + 3, 0xFFFFD6D6);
    }

    private static String message() {
        if (watcherCount > 1) {
            return watcherName + " \u7b49 " + watcherCount + " \u4e2a\u76ee\u6807\u6b63\u5728\u6ce8\u89c6";
        }
        if (alertType == TYPE_ANCHOR) {
            return "[\u951a\u70b9] " + watcherName + " \u6b63\u5728\u6ce8\u89c6\u951a\u70b9";
        }
        return watcherName + " \u6b63\u5728\u6ce8\u89c6\u4f60";
    }
}
