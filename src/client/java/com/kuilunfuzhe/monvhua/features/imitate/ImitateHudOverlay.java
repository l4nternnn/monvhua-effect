package com.kuilunfuzhe.monvhua.features.imitate;

import com.kuilunfuzhe.monvhua.client.imitate.ImitateClientManager;
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
        return switch (roleName) {
            case "樱羽艾玛" -> Text.literal(roleName).withColor(0xfc8eac);
            case "二阶堂希罗" -> Text.literal(roleName).withColor(0x8b0000);
            case "泽渡可可" -> Text.literal(roleName).withColor(0xff6700);
            case "橘雪莉" -> Text.literal(roleName).withColor(0x1e90ff);
            case "远野汉娜" -> Text.literal(roleName).withColor(0x5f9e3f);
            case "夏目安安" -> Text.literal(roleName).withColor(0x240090);
            case "城崎诺亚" -> Text.literal("城").formatted(Formatting.AQUA)
                    .append(Text.literal("崎").formatted(Formatting.RED))
                    .append(Text.literal("诺").formatted(Formatting.YELLOW))
                    .append(Text.literal("亚").formatted(Formatting.LIGHT_PURPLE));
            case "莲见蕾雅" -> Text.literal(roleName).formatted(Formatting.GOLD);
            case "佐伯米利亚" -> Text.literal(roleName).formatted(Formatting.YELLOW);
            case "黑部奈叶香" -> Text.literal(roleName).formatted(Formatting.DARK_GRAY);
            case "宝生玛格" -> Text.literal(roleName).formatted(Formatting.DARK_PURPLE);
            case "紫藤亚里沙" -> Text.literal(roleName).withColor(0xB1B7AC);
            case "冰上梅露露" -> Text.literal(roleName).withColor(0xddb6ff);
            case "典狱长" -> Text.literal(roleName).formatted(Formatting.DARK_RED);
            case "月代雪" -> Text.literal(roleName).withColor(0xe0ffff);
            default -> Text.literal(roleName).formatted(Formatting.LIGHT_PURPLE);
        };
    }
}