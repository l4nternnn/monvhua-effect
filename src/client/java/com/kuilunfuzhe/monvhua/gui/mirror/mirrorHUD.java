package com.kuilunfuzhe.monvhua.gui.mirror;

import com.kuilunfuzhe.monvhua.features.mirror.MirrorClientManager;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public class mirrorHUD {
    public static void register() {
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            if (!MirrorClientManager.isCharging()) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;

            int screenWidth = drawContext.getScaledWindowWidth();
            int screenHeight = drawContext.getScaledWindowHeight();
            int barWidth = 80;
            int barHeight = 5;
            int x = (screenWidth - barWidth) / 2;
            int y = screenHeight - 49;

            drawContext.fill(x, y, x + barWidth, y + barHeight, 0xFF444444);
            int fillWidth = (int) (barWidth * MirrorClientManager.getChargeRatio());
            drawContext.fill(x, y, x + fillWidth, y + barHeight, 0xFFFFAA00);

            String text = String.format("充能: %d%%", (int) (MirrorClientManager.getChargeRatio() * 100));
            drawContext.drawText(client.textRenderer, text, x + barWidth + 5, y - 2, 0xFFFFAA, true);
        });
    }
}
