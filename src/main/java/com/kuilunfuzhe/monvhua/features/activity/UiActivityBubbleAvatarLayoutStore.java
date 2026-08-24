package com.kuilunfuzhe.monvhua.features.activity;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/** Server-authoritative, globally shared avatar layout configuration. */
public final class UiActivityBubbleAvatarLayoutStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir()
            .resolve("monvhua_bubble_avatar_layout.json");
    private static final UiActivityBubbleAvatarLayoutStore INSTANCE = new UiActivityBubbleAvatarLayoutStore();

    private final Map<Integer, UiActivityBubbleAvatarLayout> layouts = new LinkedHashMap<>();

    private UiActivityBubbleAvatarLayoutStore() {
        resetDefaults();
        load();
    }

    public static UiActivityBubbleAvatarLayoutStore get() {
        return INSTANCE;
    }

    public UiActivityBubbleAvatarLayout layout(int avatarId) {
        if (UiActivityBubbleAvatarCatalog.key(avatarId).isEmpty()) {
            return UiActivityBubbleAvatarLayout.defaults(avatarId);
        }
        return layouts.computeIfAbsent(avatarId, id -> UiActivityBubbleAvatarLayout.defaults(id));
    }

    public Map<Integer, UiActivityBubbleAvatarLayout> snapshot() {
        return Map.copyOf(layouts);
    }

    public boolean set(int avatarId, float centerX, float centerY, float scale) {
        if (UiActivityBubbleAvatarCatalog.key(avatarId).isEmpty()) {
            return false;
        }
        UiActivityBubbleAvatarLayout next = UiActivityBubbleAvatarLayout.sanitize(
                centerX, centerY, scale, avatarId);
        UiActivityBubbleAvatarLayout previous = layout(avatarId);
        if (previous.equals(next)) {
            return false;
        }
        layouts.put(avatarId, next);
        save();
        return true;
    }

    public void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Path temp = PATH.resolveSibling(PATH.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temp)) {
                Map<String, UiActivityBubbleAvatarLayout> encoded = new LinkedHashMap<>();
                for (Map.Entry<Integer, UiActivityBubbleAvatarLayout> entry : layouts.entrySet()) {
                    String key = UiActivityBubbleAvatarCatalog.key(entry.getKey());
                    if (!key.isEmpty()) {
                        encoded.put(key, entry.getValue());
                    }
                }
                GSON.toJson(encoded, writer);
            }
            try {
                Files.move(temp, PATH, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, PATH, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception ignored) {
            // A failed write must not interrupt the server tick or rendering state sync.
        }
    }

    private void load() {
        if (!Files.isRegularFile(PATH)) {
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(PATH)) {
            Map<?, ?> encoded = GSON.fromJson(reader, Map.class);
            if (encoded == null) {
                return;
            }
            boolean migrated = false;
            for (Map.Entry<?, ?> entry : encoded.entrySet()) {
                int avatarId = UiActivityBubbleAvatarCatalog.fromKey(String.valueOf(entry.getKey()));
                if (avatarId == UiActivityBubbleAvatarCatalog.NONE || !(entry.getValue() instanceof Map<?, ?> values)) {
                    continue;
                }
                float x = number(values.get("centerX"), Float.NaN);
                float y = number(values.get("centerY"), Float.NaN);
                float scale = number(values.get("scale"), Float.NaN);
                // Migrate only the defaults written by the previous inside-bubble
                // schema; customized coordinates remain untouched.
                if (isLegacyDefault(avatarId, x, y, scale)) {
                    layouts.put(avatarId, UiActivityBubbleAvatarLayout.defaults(avatarId));
                    migrated = true;
                } else {
                    layouts.put(avatarId, UiActivityBubbleAvatarLayout.sanitize(x, y, scale, avatarId));
                }
            }
            if (migrated) save();
        } catch (Exception ignored) {
            resetDefaults();
            save();
        }
    }

    private void resetDefaults() {
        layouts.clear();
        layouts.put(UiActivityBubbleAvatarCatalog.HIRO,
                UiActivityBubbleAvatarLayout.defaults(UiActivityBubbleAvatarCatalog.HIRO));
        layouts.put(UiActivityBubbleAvatarCatalog.WEIJIE,
                UiActivityBubbleAvatarLayout.defaults(UiActivityBubbleAvatarCatalog.WEIJIE));
        layouts.put(UiActivityBubbleAvatarCatalog.NOA,
                UiActivityBubbleAvatarLayout.defaults(UiActivityBubbleAvatarCatalog.NOA));
    }

    private static float number(Object value, float fallback) {
        return value instanceof Number number ? number.floatValue() : fallback;
    }

    private static boolean isLegacyDefault(int avatarId, float x, float y, float scale) {
        return near(x, 0.78F) && near(y, 0.78F)
                && ((avatarId == UiActivityBubbleAvatarCatalog.NOA && near(scale, 0.20F))
                || (avatarId == UiActivityBubbleAvatarCatalog.WEIJIE && near(scale, 0.17F))
                || (avatarId == UiActivityBubbleAvatarCatalog.HIRO && near(scale, 0.18F)));
    }

    private static boolean near(float a, float b) {
        return Float.isFinite(a) && Math.abs(a - b) < 0.0001F;
    }
}
