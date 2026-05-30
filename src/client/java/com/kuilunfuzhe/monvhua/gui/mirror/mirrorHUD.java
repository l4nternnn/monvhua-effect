package com.kuilunfuzhe.monvhua.gui.mirror;

import com.kuilunfuzhe.monvhua.features.mirror.MirrorClientManager;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * 镜子充能进度条 HUD。
 * 在屏幕底部居中显示一条充能进度条，仅在镜子正在充能时渲染。
 * 进度条宽度80px，颜色为橙色（0xFFFFAA00），显示充能百分比文字。
 */
public class mirrorHUD {
    /**
     * 注册 HUD 渲染回调，每帧检测充能状态并绘制进度条。
     */
    public static void register() {
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            if (!MirrorClientManager.isCharging()) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;

            int screenWidth = drawContext.getScaledWindowWidth();
            int screenHeight = drawContext.getScaledWindowHeight();
            // 进度条尺寸：宽80px、高5px
            int barWidth = 80;
            int barHeight = 5;
            // 水平居中，垂直距底部49px（避开快捷栏）
            int x = (screenWidth - barWidth) / 2;
            int y = screenHeight - 49;

            // 深灰色背景条
            drawContext.fill(x, y, x + barWidth, y + barHeight, 0xFF444444);
            // 橙色填充部分（按充能比例）
            int fillWidth = (int) (barWidth * MirrorClientManager.getChargeRatio());
            drawContext.fill(x, y, x + fillWidth, y + barHeight, 0xFFFFAA00);

            // 充能百分比文字，显示在进度条右侧
            String text = String.format("充能: %d%%", (int) (MirrorClientManager.getChargeRatio() * 100));
            drawContext.drawText(client.textRenderer, text, x + barWidth + 5, y - 2, 0xFFFFAA, true);
        });
    }
}
