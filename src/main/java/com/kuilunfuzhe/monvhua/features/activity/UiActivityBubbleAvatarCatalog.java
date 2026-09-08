package com.kuilunfuzhe.monvhua.features.activity;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/** Data-driven avatar definitions shared by the client and server. */
public final class UiActivityBubbleAvatarCatalog {
    public static final int NONE = 0;
    /** Legacy IDs retained only for old layout/config migration. */
    public static final int HIRO = 1;
    public static final int WEIJIE = 2;
    public static final int NOA = 3;
    private static final String MANIFEST = "assets/monvhua/avatar_bubbles.json";
    private static volatile CatalogData data;

    private UiActivityBubbleAvatarCatalog() {
    }

    public static List<Definition> definitions() {
        return data().ordered;
    }

    public static Definition definition(String key) {
        if (key == null) return null;
        return data().byKey.get(normalize(key));
    }

    public static boolean contains(String key) {
        return definition(key) != null;
    }

    public static String normalize(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }

    public static String key(int legacyId) {
        return switch (legacyId) {
            case HIRO -> "hiro";
            case WEIJIE -> "weijie";
            case NOA -> "noa";
            default -> {
                Definition definition = data().byId.get(legacyId);
                yield definition == null ? "" : definition.key();
            }
        };
    }

    /** Compatibility alias for old config readers. */
    public static int fromKey(String key) {
        String normalized = normalize(key);
        // Older layout files used numeric object keys. Keep those files
        // readable while all new data is keyed by the manifest string.
        try {
            if (!normalized.isEmpty() && normalized.chars().allMatch(Character::isDigit)) {
                return legacyId(key(Integer.parseInt(normalized)));
            }
        } catch (NumberFormatException ignored) {
            return NONE;
        }
        return legacyId(normalized);
    }

    public static int legacyId(String key) {
        return switch (normalize(key)) {
            case "hiro" -> HIRO;
            case "weijie" -> WEIJIE;
            case "noa" -> NOA;
            default -> {
                Definition definition = definition(key);
                yield definition == null ? NONE : definition.id();
            }
        };
    }

    public static String fromLegacyId(int legacyId) {
        return key(legacyId);
    }

    public static Identifier textureId(String key) {
        Definition definition = definition(key);
        return definition == null ? null : definition.texture();
    }

    public static Identifier textureId(int legacyId) {
        return textureId(key(legacyId));
    }

    public static boolean isPixel(String key) {
        Definition definition = definition(key);
        return definition != null && definition.mode() == Mode.PIXEL;
    }

    public static boolean isPixel(int legacyId) {
        return isPixel(key(legacyId));
    }

    public static int resolveTags(Set<String> tags) {
        if (tags == null || tags.isEmpty()) return NONE;
        Definition selected = null;
        for (Definition definition : definitions()) {
            if (containsTag(tags, definition.key())) {
                selected = definition;
                break;
            }
        }
        return selected == null ? NONE : legacyId(selected.key());
    }

    public static String resolveTagKey(Set<String> tags) {
        if (tags == null || tags.isEmpty()) return "";
        for (Definition definition : definitions()) {
            if (containsTag(tags, definition.key())) return definition.key();
        }
        return "";
    }

    private static boolean containsTag(Set<String> tags, String expected) {
        for (String tag : tags) {
            String normalized = normalize(tag);
            if (normalized.equals(expected) || normalized.equals("monvhua_avatar:" + expected)) {
                return true;
            }
        }
        return false;
    }

    private static CatalogData data() {
        CatalogData current = data;
        if (current != null) return current;
        synchronized (UiActivityBubbleAvatarCatalog.class) {
            if (data == null) data = load();
            return data;
        }
    }

    private static CatalogData load() {
        Map<String, Definition> definitions = new LinkedHashMap<>();
        try {
            Path manifest = FabricLoader.getInstance().getModContainer("monvhua")
                    .flatMap(container -> container.findPath(MANIFEST)).orElse(null);
            if (manifest != null) {
                try (Reader reader = Files.newBufferedReader(manifest)) {
                    JsonElement root = JsonParser.parseReader(reader);
                    if (root.isJsonArray()) {
                        for (JsonElement element : root.getAsJsonArray()) {
                            Definition definition = parse(element.getAsJsonObject());
                            if (definition != null) definitions.putIfAbsent(definition.key(), definition);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Built-in fallback below keeps old installations usable.
        }
        if (definitions.isEmpty()) {
            addFallback(definitions, "hiro", "textures/character_bubble/hiro.png", Mode.SMOOTH,
                    712.0F / 787.0F, 1.10F, 0.82F, 0.22F, 30);
            addFallback(definitions, "weijie", "textures/character_bubble/weijie.png", Mode.SMOOTH,
                    915.0F / 918.0F, 1.10F, 0.82F, 0.22F, 20);
            addFallback(definitions, "noa", "textures/character_bubble/noa.png", Mode.PIXEL,
                    32.0F / 33.0F, 1.12F, 0.82F, 0.22F, 10);
        }
        List<Definition> ordered = new ArrayList<>(definitions.values());
        ordered.sort(Comparator.comparingInt(Definition::priority).reversed()
                .thenComparing(Definition::key));
        Map<Integer, Definition> byId = new LinkedHashMap<>();
        Set<Integer> usedIds = new HashSet<>();
        for (int i = 0; i < ordered.size(); i++) {
            Definition definition = ordered.get(i);
            int id = definition.id();
            while (id == NONE || !usedIds.add(id)) {
                id = id == Integer.MAX_VALUE ? 4 : id + 1;
            }
            if (id != definition.id()) {
                definition = new Definition(definition.key(), definition.texture(), definition.mode(),
                        definition.aspect(), definition.defaultCenterX(), definition.defaultCenterY(),
                        definition.defaultScale(), definition.priority(), id);
                ordered.set(i, definition);
                definitions.put(definition.key(), definition);
            }
            byId.put(id, definition);
        }
        return new CatalogData(Map.copyOf(definitions), Map.copyOf(byId), List.copyOf(ordered));
    }

    private static Definition parse(JsonObject object) {
        String key = normalize(string(object, "key", ""));
        Identifier texture = parseIdentifier(string(object, "texture", ""));
        if (key.isEmpty() || texture == null) return null;
        Mode mode = "pixel".equalsIgnoreCase(string(object, "mode", "smooth"))
                ? Mode.PIXEL : Mode.SMOOTH;
        JsonObject defaults = object.has("default") && object.get("default").isJsonObject()
                ? object.getAsJsonObject("default") : new JsonObject();
        return new Definition(key, texture, mode, number(object, "aspect", 1.0F),
                number(defaults, "centerX", 1.10F), number(defaults, "centerY", 0.82F),
                number(defaults, "scale", 0.22F), object.has("priority")
                ? object.get("priority").getAsInt() : 0, stableId(key));
    }

    private static void addFallback(Map<String, Definition> definitions, String key, String path,
                                    Mode mode, float aspect, float x, float y, float scale, int priority) {
        definitions.put(key, new Definition(key, Identifier.of("monvhua", path), mode, aspect, x, y, scale, priority,
                stableId(key)));
    }

    private static int stableId(String key) {
        // Preserve the wire/config IDs used by released versions.
        switch (normalize(key)) {
            case "hiro" -> { return HIRO; }
            case "weijie" -> { return WEIJIE; }
            case "noa" -> { return NOA; }
            default -> { }
        }
        int id = key.hashCode() & 0x7FFFFFFF;
        return id < 4 ? id + 4 : id;
    }

    private static Identifier parseIdentifier(String value) {
        try {
            if (value == null || value.isBlank()) return null;
            int separator = value.indexOf(':');
            return separator < 0 ? Identifier.of("monvhua", value)
                    : Identifier.of(value.substring(0, separator), value.substring(separator + 1));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String string(JsonObject object, String key, String fallback) {
        return object.has(key) && object.get(key).isJsonPrimitive()
                ? object.get(key).getAsString() : fallback;
    }

    private static float number(JsonObject object, String key, float fallback) {
        try {
            return object.has(key) ? object.get(key).getAsFloat() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public enum Mode { SMOOTH, PIXEL }

    public record Definition(String key, Identifier texture, Mode mode, float aspect,
                             float defaultCenterX, float defaultCenterY,
                             float defaultScale, int priority, int id) {
    }

    private record CatalogData(Map<String, Definition> byKey, Map<Integer, Definition> byId,
                               List<Definition> ordered) {
    }
}
