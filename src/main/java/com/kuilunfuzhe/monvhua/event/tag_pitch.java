package com.kuilunfuzhe.monvhua.event;

import net.minecraft.entity.Entity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class tag_pitch {
    private static final Map<String, String> TAG_NAMES;
    private static final Map<String, Text> TAG_COLORED_NAMES;
    private static final Map<String, Text> NAME_COLORED_NAMES;
    private static final Pattern TAG_TOKEN_PATTERN;
    private static final Pattern COLORED_TOKEN_PATTERN;

    static {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("ema", "樱羽艾玛");
        names.put("cero", "二阶堂希罗");
        names.put("nnk", "黑部奈叶香");
        names.put("mago", "宝生玛格");
        names.put("leiya", "莲见蕾雅");
        names.put("milya", "佐伯米利亚");
        names.put("sherry", "橘雪莉");
        names.put("yalisa", "紫藤亚里沙");
        names.put("noa", "城崎诺亚");
        names.put("anan", "夏目安安");
        names.put("yuki", "月代雪");
        names.put("mll", "冰上梅露露");
        names.put("coco", "泽渡可可");
        names.put("hanna", "远野汉娜");
        TAG_NAMES = Collections.unmodifiableMap(names);
        TAG_COLORED_NAMES = Collections.unmodifiableMap(createColoredNames(names));
        NAME_COLORED_NAMES = Collections.unmodifiableMap(createNameColoredNames(TAG_COLORED_NAMES));
        TAG_TOKEN_PATTERN = Pattern.compile("(?<![A-Za-z0-9_])@?(" + String.join("|", TAG_NAMES.keySet()) + ")(?![A-Za-z0-9_])", Pattern.CASE_INSENSITIVE);
        COLORED_TOKEN_PATTERN = Pattern.compile(
                "(?<![A-Za-z0-9_])@?(" + String.join("|", TAG_NAMES.keySet()) + ")(?![A-Za-z0-9_])"
                        + "|" + String.join("|", NAME_COLORED_NAMES.keySet().stream().map(Pattern::quote).toList()),
                Pattern.CASE_INSENSITIVE);
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

    public static Text coloredNameForTag(String tag) {
        String key = keyForTag(tag);
        Text text = key == null ? null : TAG_COLORED_NAMES.get(key);
        if (text != null) {
            return text.copy();
        }
        return Text.literal("◆ " + nameForTag(tag)).formatted(Formatting.LIGHT_PURPLE);
    }

    public static Text coloredNameForName(String name) {
        if (name == null || name.isBlank()) {
            return Text.literal("◆ 未知").formatted(Formatting.LIGHT_PURPLE);
        }
        String trimmed = name.trim();
        String key = keyForTag(trimmed);
        if (key != null) {
            return TAG_COLORED_NAMES.get(key).copy();
        }
        Text text = NAME_COLORED_NAMES.get(trimmed);
        if (text != null) {
            return text.copy();
        }
        Text replaced = coloredTextReplacingTags(name);
        if (replaced != null) {
            return replaced;
        }
        return Text.literal("◆ " + replaceTags(name)).formatted(Formatting.LIGHT_PURPLE);
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

    public static Text entityColoredName(Entity entity) {
        return entityColoredName(entity, "未知");
    }

    public static Text entityColoredName(Entity entity, String fallback) {
        if (entity == null) {
            return Text.literal("◆ " + fallback).formatted(Formatting.LIGHT_PURPLE);
        }
        for (String tag : entity.getCommandTags()) {
            String key = keyForTag(tag);
            if (key != null) {
                return TAG_COLORED_NAMES.get(key).copy();
            }
        }
        return coloredNameForName(entity.getName().getString());
    }

    public static String tagForEntity(Entity entity) {
        if (entity == null) {
            return "";
        }
        for (String tag : entity.getCommandTags()) {
            String key = keyForTag(tag);
            if (key != null) {
                return key;
            }
        }
        return "";
    }

    public static Text coloredNameForTagOrName(String tag, String name) {
        if (hasName(tag)) {
            return coloredNameForTag(tag);
        }
        return coloredNameForName(name);
    }

    private static String rawNameForTag(String tag) {
        String key = keyForTag(tag);
        return key == null ? null : TAG_NAMES.get(key);
    }

    private static String keyForTag(String tag) {
        if (tag == null) {
            return null;
        }
        String key = tag.trim().toLowerCase(Locale.ROOT);
        String name = TAG_NAMES.get(key);
        if (name != null) {
            return key;
        }
        int namespaceIndex = key.lastIndexOf(':');
        if (namespaceIndex >= 0 && namespaceIndex < key.length() - 1) {
            String unqualified = key.substring(namespaceIndex + 1);
            return TAG_NAMES.containsKey(unqualified) ? unqualified : null;
        }
        return null;
    }

    private static Map<String, Text> createColoredNames(Map<String, String> names) {
        Map<String, Text> colored = new LinkedHashMap<>();
        colored.put("ema", solid(names.get("ema"), 0xfc8eac));
        colored.put("cero", solid(names.get("cero"), 0x8b0000));
        colored.put("nnk", Text.literal("◆ ").formatted(Formatting.DARK_GRAY)
                .append(Text.literal(names.get("nnk")).formatted(Formatting.DARK_GRAY)));
        colored.put("mago", Text.literal("◆ ").formatted(Formatting.DARK_PURPLE)
                .append(Text.literal(names.get("mago")).formatted(Formatting.DARK_PURPLE)));
        colored.put("leiya", Text.literal("◆ ").formatted(Formatting.GOLD)
                .append(Text.literal(names.get("leiya")).formatted(Formatting.GOLD)));
        colored.put("milya", Text.literal("◆ ").formatted(Formatting.YELLOW)
                .append(Text.literal(names.get("milya")).formatted(Formatting.YELLOW)));
        colored.put("sherry", solid(names.get("sherry"), 0x1e90ff));
        colored.put("yalisa", solid(names.get("yalisa"), 0xB1B7AC));
        colored.put("noa", Text.literal("◆ ").formatted(Formatting.AQUA)
                .append(Text.literal("城").formatted(Formatting.AQUA))
                .append(Text.literal("崎").formatted(Formatting.RED))
                .append(Text.literal("诺").formatted(Formatting.YELLOW))
                .append(Text.literal("亚").formatted(Formatting.LIGHT_PURPLE)));
        colored.put("anan", solid(names.get("anan"), 0x240090));
        colored.put("yuki", solid(names.get("yuki"), 0xe0ffff));
        colored.put("mll", solid(names.get("mll"), 0xddb6ff));
        colored.put("coco", solid(names.get("coco"), 0xff6700));
        colored.put("hanna", solid(names.get("hanna"), 0x5f9e3f));
        return colored;
    }

    private static Map<String, Text> createNameColoredNames(Map<String, Text> coloredByTag) {
        Map<String, Text> colored = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : TAG_NAMES.entrySet()) {
            Text text = coloredByTag.get(entry.getKey());
            if (text != null) {
                colored.put(entry.getValue(), text);
            }
        }
        colored.put("城崎诺亚", coloredByTag.get("noa"));
        colored.put("佐伯米莉亚", coloredByTag.get("milya"));
        colored.put("典狱长", Text.literal("◆ ").formatted(Formatting.WHITE)
                .append(Text.literal("典狱长").formatted(Formatting.WHITE)));
        return colored;
    }

    private static Text coloredTextReplacingTags(String text) {
        Matcher matcher = COLORED_TOKEN_PATTERN.matcher(text);
        MutableText result = Text.empty();
        int lastEnd = 0;
        boolean matched = false;
        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                result.append(Text.literal(text.substring(lastEnd, matcher.start())).formatted(Formatting.LIGHT_PURPLE));
            }
            Text colored = coloredToken(matcher);
            if (colored != null) {
                result.append(colored.copy());
                matched = true;
            } else {
                result.append(Text.literal(matcher.group()).formatted(Formatting.LIGHT_PURPLE));
            }
            lastEnd = matcher.end();
        }
        if (!matched) {
            return null;
        }
        if (lastEnd < text.length()) {
            result.append(Text.literal(text.substring(lastEnd)).formatted(Formatting.LIGHT_PURPLE));
        }
        return result;
    }

    private static Text coloredToken(MatchResult match) {
        String tag = match.group(1);
        if (tag != null) {
            String key = keyForTag(tag);
            return key == null ? null : TAG_COLORED_NAMES.get(key);
        }
        return NAME_COLORED_NAMES.get(match.group());
    }

    private static MutableText solid(String name, int color) {
        return Text.literal("◆ ").withColor(color).append(Text.literal(name).withColor(color));
    }
}
