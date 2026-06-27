package com.kuilunfuzhe.monvhua.fantasy;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class FantasyRenderer {

    private static final Random RANDOM = new Random();
    private static final List<ActiveText> activeTexts = new ArrayList<>();

    private static final int DURATION_MIN = 120;
    private static final int DURATION_MAX = 200;
    private static final int FADE_IN_TICKS = 30;
    private static final int FADE_OUT_TICKS = 30;
    private static final int SHAKE_AMOUNT = 2;

    // 字号缩放（调整为原来的 3/4）
    private static final float FONT_SCALE_BURST = 3.0f;
    private static final float FONT_SCALE_EDGE = 1.8f;

    public static void register() {
        HudRenderCallback.EVENT.register(FantasyRenderer::render);
    }

    public static void showText(String text, int durationTicks, boolean isBurst, boolean isGlitch, String colorCode) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        int actualDuration = durationTicks;
        if (actualDuration < DURATION_MIN) {
            actualDuration = DURATION_MIN + RANDOM.nextInt(DURATION_MAX - DURATION_MIN);
        }

        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();

        int x = 0, y = 0;
        if (isBurst) {
            // 爆发期：水平范围扩大，垂直覆盖 20%~75% 屏幕高度
            x = screenWidth / 2 + RANDOM.nextInt(400) - 200;
            y = (int)(screenHeight * 0.20) + RANDOM.nextInt((int)(screenHeight * 0.55));
        } else {
            int edge = RANDOM.nextInt(4);
            if (edge == 0) {
                x = 10 + RANDOM.nextInt(150);
                y = 20 + RANDOM.nextInt(80);
            } else if (edge == 1) {
                x = screenWidth - 10 - RANDOM.nextInt(150) - 80;
                y = 20 + RANDOM.nextInt(80);
            } else if (edge == 2) {
                x = 10 + RANDOM.nextInt(150);
                y = screenHeight - 60 - RANDOM.nextInt(80);
            } else {
                x = screenWidth - 10 - RANDOM.nextInt(150) - 80;
                y = screenHeight - 60 - RANDOM.nextInt(80);
            }
        }

        float scale = isBurst ? FONT_SCALE_BURST : FONT_SCALE_EDGE;

        activeTexts.add(new ActiveText(
                colorCode + text,
                x, y,
                scale,
                actualDuration,
                isBurst,
                isGlitch
        ));
    }

    private static void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        // 移除过期文字
        Iterator<ActiveText> iterator = activeTexts.iterator();
        while (iterator.hasNext()) {
            ActiveText active = iterator.next();
            active.age++;
            if (active.age >= active.durationTicks) {
                iterator.remove();
            }
        }

        // 分层渲染：先画乱码（底层），再画正常字幕（上层）
        for (ActiveText active : activeTexts) {
            if (!active.isGlitch) continue;
            renderText(context, active);
        }
        for (ActiveText active : activeTexts) {
            if (active.isGlitch) continue;
            renderText(context, active);
        }
    }

    private static void renderText(DrawContext context, ActiveText active) {
        MinecraftClient client = MinecraftClient.getInstance();

        float fadeIn = Math.min(1.0f, active.age / (float)FADE_IN_TICKS);
        float fadeOut = Math.min(1.0f, (active.durationTicks - active.age) / (float)FADE_OUT_TICKS);
        float alpha = Math.min(fadeIn, fadeOut);

        String displayText = active.isGlitch ? "§k" + active.text + "§r" : active.text;

        int shakeX = active.x + (RANDOM.nextInt(SHAKE_AMOUNT * 2 + 1) - SHAKE_AMOUNT);
        int shakeY = active.y + (RANDOM.nextInt(SHAKE_AMOUNT * 2 + 1) - SHAKE_AMOUNT);

        TextRenderer renderer = client.textRenderer;

        // 计算文字宽度并居中
        int textWidth = renderer.getWidth(displayText);
        int centeredX = (int)(-textWidth / 2.0f);

        var matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate((float) shakeX, (float) shakeY);
        matrices.scale(active.scale, active.scale);

        int color = (int) (alpha * 255) << 24 | 0xFFFFFF;
        context.drawText(renderer, displayText, centeredX, 0, color, true);

        matrices.popMatrix();
    }

    private static class ActiveText {
        String text;
        int x, y;
        float scale;
        int durationTicks;
        int age = 0;
        boolean isBurst;
        boolean isGlitch;

        ActiveText(String text, int x, int y, float scale, int durationTicks, boolean isBurst, boolean isGlitch) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.scale = scale;
            this.durationTicks = durationTicks;
            this.isBurst = isBurst;
            this.isGlitch = isGlitch;
        }
    }
}