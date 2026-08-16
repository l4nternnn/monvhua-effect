package com.kuilunfuzhe.monvhua.features.activity;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kuilunfuzhe.monvhua.MonvhuaMod;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class EmotionCatalog {
    private static final String MANIFEST_PATH = "/assets/monvhua/textures/emotion/manifest.json";
    private static final List<Entry> ENTRIES;
    private static final Map<Integer, Entry> BY_ID;

    static {
        List<Entry> entries = new ArrayList<>(loadEntries());
        entries.sort(java.util.Comparator.comparingInt(Entry::id));
        ENTRIES = List.copyOf(entries);
        Map<Integer, Entry> byId = new HashMap<>();
        for (Entry entry : entries) {
            byId.put(entry.id(), entry);
        }
        BY_ID = Collections.unmodifiableMap(byId);
    }

    private EmotionCatalog() {
    }

    public static List<Entry> entries() {
        return ENTRIES;
    }

    public static Entry byId(int id) {
        return BY_ID.get(id);
    }

    public static boolean isValidId(int id) {
        return id == 0 || BY_ID.containsKey(id);
    }

    private static List<Entry> loadEntries() {
        try (InputStream stream = EmotionCatalog.class.getResourceAsStream(MANIFEST_PATH)) {
            if (stream == null) {
                MonvhuaMod.LOGGER.warn("Missing bundled emotion manifest: {}", MANIFEST_PATH);
                return List.of();
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray array = root.getAsJsonArray("entries");
            if (array == null) {
                return List.of();
            }
            List<Entry> entries = new ArrayList<>();
            Map<Integer, Boolean> usedIds = new HashMap<>();
            for (JsonElement element : array) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject object = element.getAsJsonObject();
                int id = object.get("id").getAsInt();
                String file = object.get("file").getAsString();
                String typeName = object.get("type").getAsString().toUpperCase(Locale.ROOT);
                if (id <= 0 || id > 255 || usedIds.putIfAbsent(id, Boolean.TRUE) != null || !isSafeFile(file)) {
                    continue;
                }
                Type type = "GIF".equals(typeName) ? Type.GIF : Type.IMAGE;
                entries.add(new Entry(id, file, type,
                        Identifier.of(MonvhuaMod.MOD_ID, "textures/emotion/" + file)));
            }
            return entries;
        } catch (Exception exception) {
            MonvhuaMod.LOGGER.error("Failed to load bundled emotion manifest", exception);
            return List.of();
        }
    }

    private static boolean isSafeFile(String file) {
        return file != null
                && !file.isBlank()
                && !file.contains("..")
                && !file.contains("/")
                && !file.contains("\\")
                && file.equals(file.toLowerCase(Locale.ROOT));
    }

    public enum Type {
        IMAGE,
        GIF
    }

    public record Entry(int id, String file, Type type, Identifier resourceId) {
    }
}
