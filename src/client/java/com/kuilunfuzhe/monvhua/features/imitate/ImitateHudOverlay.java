package com.kuilunfuzhe.monvhua.features.imitate;

import com.kuilunfuzhe.monvhua.client.imitate.ImitateClientManager;
import com.kuilunfuzhe.monvhua.event.tag_pitch;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.UUID;

public class ImitateHudOverlay {

    public static void register() {
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;

            UUID uuid = client.player.getUuid();

            drawCooldownStatus(context, uuid);

            if (!ImitateClientManager.isImitating(uuid)) return;

            String roleName = ImitateClientManager.getImitateName(uuid);
            if (roleName == null) return;

            int remainingTime = ImitateClientManager.getRemainingTime(uuid);

            drawImitateStatus(context, roleName, remainingTime);
        });
    }

    private static void drawCooldownStatus(DrawContext context, UUID uuid) {
        MinecraftClient client = MinecraftClient.getInstance();
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();

        int switchCooldown = ImitateClientManager.getSwitchCooldownRemaining(uuid);
        int soundWaveCooldown = ImitateClientManager.getSoundWaveCooldownRemaining(uuid);

        int y = screenHeight - 60;

        if (switchCooldown > 0) {
            MutableText switchText = Text.literal("模仿冷却: ")
                    .formatted(Formatting.RED)
                    .append(Text.literal(switchCooldown + "秒").formatted(Formatting.YELLOW));
            int textWidth = client.textRenderer.getWidth(switchText);
            context.drawText(client.textRenderer, switchText, screenWidth / 2 - textWidth / 2, y, 0xFFFFFF, true);
            y += 12;
        }

        if (soundWaveCooldown > 0) {
            MutableText soundWaveText = Text.literal("声波震荡冷却: ")
                    .formatted(Formatting.AQUA)
                    .append(Text.literal(soundWaveCooldown + "秒").formatted(Formatting.YELLOW));
            int textWidth = client.textRenderer.getWidth(soundWaveText);
            context.drawText(client.textRenderer, soundWaveText, screenWidth / 2 - textWidth / 2, y, 0xFFFFFF, true);
        }
    }

    private static void drawImitateStatus(DrawContext context, String roleName, int remainingTime) {
        MinecraftClient client = MinecraftClient.getInstance();
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();

        int x = screenWidth / 2;
        int y = screenHeight - 80;

        MutableText statusText = Text.literal("正在模仿 ")
                .formatted(Formatting.GRAY)
                .append(getColoredRoleName(roleName))
                .append(Text.literal(" 中").formatted(Formatting.GRAY));

        if (remainingTime > 0) {
            statusText = statusText.append(Text.literal(" (" + remainingTime + "秒)").formatted(Formatting.YELLOW));
        }

        int textWidth = client.textRenderer.getWidth(statusText);
        context.drawText(client.textRenderer, statusText, x - textWidth / 2, y, 0xFFFFFF, true);
    }

    private static Text getColoredRoleName(String roleName) {
        return tag_pitch.coloredNameForName(roleName);
    }
}
