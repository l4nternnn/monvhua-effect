package com.kuilunfuzhe.monvhua.client.imitate;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SilenceClientManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("Monvhua/SilenceClient");

    private static final ConcurrentHashMap<UUID, Long> silenceEndTime = new ConcurrentHashMap<>();
    private static boolean isSilenced = false;
    private static long silenceEndTimestamp = 0;

    public static void setSilenced(UUID playerUUID, int durationSeconds) {
        long endTime = System.currentTimeMillis() + durationSeconds * 1000L;
        silenceEndTime.put(playerUUID, endTime);
        isSilenced = true;
        silenceEndTimestamp = endTime;
        LOGGER.info("玩家 {} 被静音，持续时间: {}秒", playerUUID, durationSeconds);
    }

    public static boolean isPlayerSilenced(UUID playerUUID) {
        Long endTime = silenceEndTime.get(playerUUID);
        if (endTime == null) return false;
        if (System.currentTimeMillis() >= endTime) {
            silenceEndTime.remove(playerUUID);
            isSilenced = false;
            LOGGER.info("玩家 {} 静音效果已结束", playerUUID);
            return false;
        }
        return true;
    }

    public static boolean isSilenced() {
        if (System.currentTimeMillis() >= silenceEndTimestamp) {
            isSilenced = false;
        }
        return isSilenced;
    }

    public static int getRemainingSeconds(UUID playerUUID) {
        Long endTime = silenceEndTime.get(playerUUID);
        if (endTime == null) return 0;
        long remaining = (endTime - System.currentTimeMillis()) / 1000;
        return remaining > 0 ? (int) remaining : 0;
    }

    public static void clearSilence(UUID playerUUID) {
        silenceEndTime.remove(playerUUID);
        isSilenced = false;
        LOGGER.info("玩家 {} 静音效果已清除", playerUUID);
    }

    public static Text getGarbledText(String originalText) {
        if (!isSilenced()) {
            return Text.literal(originalText);
        }

        StringBuilder garbled = new StringBuilder();
        for (char c : originalText.toCharArray()) {
            if (Character.isLetterOrDigit(c) || Character.isWhitespace(c)) {
                if (Character.isWhitespace(c)) {
                    garbled.append(c);
                } else if (Character.isLetter(c)) {
                    garbled.append((char) ('A' + (int) (Math.random() * 26)));
                } else {
                    garbled.append((char) ('0' + (int) (Math.random() * 10)));
                }
            } else {
                garbled.append(c);
            }
        }

        return Text.literal(garbled.toString())
                .formatted(Formatting.OBFUSCATED)
                .formatted(Formatting.RED);
    }
}
