package com.kuilunfuzhe.monvhua.hud;

import com.kuilunfuzhe.monvhua.features.floating.floating;
import com.kuilunfuzhe.monvhua.network.floating.FloatingEnergySyncS2CPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public class EnergyHud {

    private static double currentEnergy = 100;
    private static double maxEnergy = 100;

    public static void register() {
        // 接收服务端同步的漂浮能量数据
        ClientPlayNetworking.registerGlobalReceiver(FloatingEnergySyncS2CPacket.ID, (packet, context) -> {
            context.client().execute(() -> {
                currentEnergy = packet.currentEnergy();
                maxEnergy = packet.maxEnergy();
            });
        });

        // HUD 渲染
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;

            // 显示条件：漂浮中 或者 能量未满
            boolean shouldShow = floating.isFloating() || currentEnergy < maxEnergy;
            if (!shouldShow) return;

            int screenWidth = drawContext.getScaledWindowWidth();
            int screenHeight = drawContext.getScaledWindowHeight();
            int barWidth = 80;
            int barHeight = 5;
            int x = (screenWidth - barWidth) / 2;
            int y = screenHeight - 49;

            // 背景
            drawContext.fill(x, y, x + barWidth, y + barHeight, 0xFF444444);

            // 颜色根据能量百分比变化
            double percent = maxEnergy <= 0 ? 0 : currentEnergy / maxEnergy;
            int color;
            if (percent > 0.6) {
                color = 0xFF55AAFF; // 蓝色
            } else if (percent > 0.3) {
                color = 0xFFFFFF00; // 黄色
            } else {
                color = 0xFFFF5555; // 红色
            }

            int fillWidth = (int) (barWidth * percent);
            if (fillWidth > 0) {
                drawContext.fill(x, y, x + fillWidth, y + barHeight, color);
            }

            // 能量数值文字
            String text = String.format("%.0f/%.0f", currentEnergy, maxEnergy);
            int textX = x + barWidth + 5;
            int textY = y - 2;
            drawContext.drawText(client.textRenderer, text, textX, textY, 0xFFFFFF, true);
        });
    }
}