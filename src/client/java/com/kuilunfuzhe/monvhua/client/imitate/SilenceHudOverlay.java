package com.kuilunfuzhe.monvhua.client.imitate;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.UUID;

public class SilenceHudOverlay {

    private static final MinecraftClient client = MinecraftClient.getInstance();

    public static void register() {
        HudRenderCallback.EVENT.register(SilenceHudOverlay::render);
    }

    public static void render(DrawContext context, RenderTickCounter tickCounter) {
        if (client.player == null) return;

        UUID playerUUID = client.player.getUuid();
        if (!SilenceClientManager.isPlayerSilenced(playerUUID)) return;

        int remainingSeconds = SilenceClientManager.getRemainingSeconds(playerUUID);
        if (remainingSeconds <= 0) return;

        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();

        Text message = Text.literal("你似乎什么都听不见了")
                .formatted(Formatting.RED, Formatting.BOLD);

        int textWidth = client.textRenderer.getWidth(message);
        int x = (screenWidth - textWidth) / 2;
        int y = screenHeight - 70;

        context.drawTextWithShadow(client.textRenderer, message, x, y, 0xFFFFFF);
    }
}