package com.kuilunfuzhe.monvhua.event;

import net.minecraft.entity.Entity;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class tag_pitch {
    private static final Map<String, String> TAG_NAMES;
    private static final Pattern TAG_TOKEN_PATTERN;

    static {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("ema", "樱羽艾玛");
        names.put("cero", "二阶堂希罗");
        names.put("nnk", "黑部奈叶香");
        names.put("mago", "宝生玛格");
        names.put("leiya", "莲见蕾雅");
        names.put("milya", "佐伯米莉亚");
        names.put("sherry", "");
        names.put("yalisa", "紫藤亚里沙");
        names.put("noa", "城琦诺亚");
        names.put("anan", "夏目安安");
        names.put("yuki", "月代雪");
        names.put("mll", "冰上梅露露");
        names.put("coco", "泽渡可可");
        names.put("hanna", "远野汉娜");
        TAG_NAMES = Collections.unmodifiableMap(names);
        TAG_TOKEN_PATTERN = Pattern.compile("(?<![A-Za-z0-9_])@?(" + String.join("|", TAG_NAMES.keySet()) + ")(?![A-Za-z0-9_])", Pattern.CASE_INSENSITIVE);
    }

    private tag_pitch() {
    }

    public static Map<String, String> mappings() {
        return TAG_NAMES;
    }

    public static boolean hasName(String tag) {
        String name = rawNameForTag(tag);
        return name != null && !name.isBlank();
    }

    public static String nameForTag(String tag) {
        String name = rawNameForTag(tag);
        return name == null || name.isBlank() ? tag : name;
    }

    public static String replaceTags(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        Matcher matcher = TAG_TOKEN_PATTERN.matcher(text);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String replacement = nameForTag(matcher.group(1));
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    public static String entityDisplayName(Entity entity) {
        return entityDisplayName(entity, "未知");
    }

    public static String entityDisplayName(Entity entity, String fallback) {
        if (entity == null) {
            return fallback;
        }
        for (String tag : entity.getCommandTags()) {
            String name = rawNameForTag(tag);
            if (name != null && !name.isBlank()) {
                return name;
            }
        }
        return replaceTags(entity.getName().getString());
    }

    private static String rawNameForTag(String tag) {
        if (tag == null) {
            return null;
        }
        String key = tag.trim().toLowerCase(Locale.ROOT);
        String name = TAG_NAMES.get(key);
        if (name != null) {
            return name;
        }
        int namespaceIndex = key.lastIndexOf(':');
        if (namespaceIndex >= 0 && namespaceIndex < key.length() - 1) {
            return TAG_NAMES.get(key.substring(namespaceIndex + 1));
        }
        return null;
    }
}
