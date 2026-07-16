package com.kuilunfuzhe.monvhua.features.evil_eyes;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public final class ClairvoyanceEnergyClient {
    private static double currentEnergy = 100.0D;
    private static double maxEnergy = 100.0D;

    private ClairvoyanceEnergyClient() {
    }

    public static void initialize() {
        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.currentScreen instanceof com.kuilunfuzhe.monvhua.gui.evil_eyes.Evil_eyesScreen) {
                return;
            }
            boolean holdingClairvoyance = client.player.getMainHandStack().getItem() == com.kuilunfuzhe.monvhua.features.evil_eyes.Evil_Eyes.CLAIRVOYANCE_ITEM
                    || client.player.getOffHandStack().getItem() == com.kuilunfuzhe.monvhua.features.evil_eyes.Evil_Eyes.CLAIRVOYANCE_ITEM;
            boolean showEnergy = holdingClairvoyance || currentEnergy < maxEnergy;
            boolean showGazeAlert = ClairvoyanceGazeAlertClient.hasActiveAlert()
                    && ClairvoyanceGazeAlertClient.hasClairvoyanceInInventory(client.player);
            if (!showEnergy && !showGazeAlert) {
                return;
            }
            int x = 8;
            int y = context.getScaledWindowHeight() / 2 - 4;
            if (showEnergy) {
                renderBar(context, x, y, 92, 7, true);
            }
            if (showGazeAlert) {
                ClairvoyanceGazeAlertClient.render(context, x, y + 11);
            }
        });
    }

    public static void setEnergy(double current, double max) {
        maxEnergy = Math.max(1.0D, max);
        currentEnergy = Math.clamp(current, 0.0D, maxEnergy);
    }

    public static void renderBar(DrawContext context, int x, int y, int width, int height, boolean text) {
        if (width <= 0 || height <= 0) {
            return;
        }
        double percent = Math.clamp(currentEnergy / Math.max(1.0D, maxEnergy), 0.0D, 1.0D);
        context.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xAA1E1530);
        context.fill(x, y, x + width, y + height, 0xFF3A3540);
        int fillWidth = (int) Math.round(width * percent);
        if (percent > 0.0D && fillWidth <= 0) {
            fillWidth = 1;
        }
        for (int i = 0; i < fillWidth; i++) {
            double p = width <= 1 ? percent : (i + 1) / (double) width;
            context.fill(x + i, y, x + i + 1, y + height, colorAt(p));
        }
        if (text) {
            MinecraftClient client = MinecraftClient.getInstance();
            String label = String.format("%.0f/%.0f", currentEnergy, maxEnergy);
            context.drawText(client.textRenderer, label, x + width + 5, y - 2, 0xFFF5E6D3, true);
        }
    }

    private static int colorAt(double percent) {
        percent = Math.clamp(percent, 0.0D, 1.0D);
        if (percent <= 0.25D) {
            return lerpColor(0xFF777777, 0xFFB91C1C, percent / 0.25D);
        }
        if (percent <= 0.5D) {
            return lerpColor(0xFFB91C1C, 0xFFD97706, (percent - 0.25D) / 0.25D);
        }
        return lerpColor(0xFFD97706, 0xFF22C55E, (percent - 0.5D) / 0.5D);
    }

    private static int lerpColor(int from, int to, double amount) {
        amount = Math.clamp(amount, 0.0D, 1.0D);
        int a = lerp((from >>> 24) & 0xFF, (to >>> 24) & 0xFF, amount);
        int r = lerp((from >>> 16) & 0xFF, (to >>> 16) & 0xFF, amount);
        int g = lerp((from >>> 8) & 0xFF, (to >>> 8) & 0xFF, amount);
        int b = lerp(from & 0xFF, to & 0xFF, amount);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int lerp(int from, int to, double amount) {
        return (int) Math.round(from + (to - from) * amount);
    }
}
