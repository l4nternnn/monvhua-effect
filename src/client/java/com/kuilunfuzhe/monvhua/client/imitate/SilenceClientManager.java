package com.kuilunfuzhe.monvhua.client.imitate;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SilenceClientManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("Monvhua/SilenceClient");

    private static final ConcurrentHashMap<UUID, Long> silenceEndTime = new ConcurrentHashMap<>();
    private static boolean isSilenced = false;
    private static long silenceEndTimestamp = 0;

    private static final Map<String, RoleInfo> TAG_TO_ROLE = new HashMap<>();

    static {
        TAG_TO_ROLE.put("ema", new RoleInfo("樱羽艾玛", 0xfc8eac));
        TAG_TO_ROLE.put("cero", new RoleInfo("二阶堂希罗", 0x8b0000));
        TAG_TO_ROLE.put("nnk", new RoleInfo("黑部奈叶香", 0x555555));
        TAG_TO_ROLE.put("mago", new RoleInfo("宝生玛格", 0xAA00AA));
        TAG_TO_ROLE.put("leiya", new RoleInfo("莲见蕾雅", 0xFFAA00));
        TAG_TO_ROLE.put("milya", new RoleInfo("佐伯米利亚", 0xFFFF55));
        TAG_TO_ROLE.put("sherry", new RoleInfo("橘雪莉", 0x1e90ff));
        TAG_TO_ROLE.put("yalisa", new RoleInfo("紫藤亚里沙", 0xB1B7AC));
        TAG_TO_ROLE.put("noa", new RoleInfo("城崎诺亚", 0x55FFFF));
        TAG_TO_ROLE.put("anan", new RoleInfo("夏目安安", 0x240090));
        TAG_TO_ROLE.put("yuki", new RoleInfo("月代雪", 0xe0ffff));
        TAG_TO_ROLE.put("mll", new RoleInfo("冰上梅露露", 0xddb6ff));
        TAG_TO_ROLE.put("coco", new RoleInfo("泽渡可可", 0xff6700));
        TAG_TO_ROLE.put("hanna", new RoleInfo("远野汉娜", 0x5f9e3f));
    }

    private static class RoleInfo {
        final String name;
        final int color;

        RoleInfo(String name, int color) {
            this.name = name;
            this.color = color;
        }
    }

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

    public static String garbleText(String originalText) {
        if (!isSilenced()) return originalText;

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
        return garbled.toString();
    }

    public static Text getGarbledText(Text originalText) {
        if (!isSilenced()) return originalText;

        String plainText = originalText.getString();
        String garbled = garbleText(plainText);
        return Text.literal(garbled).formatted(Formatting.RED);
    }

    public static RoleInfo getRoleInfoByTag(String tag) {
        return TAG_TO_ROLE.get(tag);
    }
}