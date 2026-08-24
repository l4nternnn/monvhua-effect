package com.kuilunfuzhe.monvhua.features.activity;

import net.minecraft.util.Identifier;

import java.util.Locale;
import java.util.Set;

/** Shared, server-validated avatar names and client resource IDs. */
public final class UiActivityBubbleAvatarCatalog {
    public static final int NONE = 0;
    public static final int HIRO = 1;
    public static final int WEIJIE = 2;
    public static final int NOA = 3;

    private UiActivityBubbleAvatarCatalog() {
    }

    public static int fromKey(String key) {
        if (key == null) {
            return NONE;
        }
        return switch (key.toLowerCase(Locale.ROOT)) {
            case "hiro" -> HIRO;
            case "weijie" -> WEIJIE;
            case "noa" -> NOA;
            default -> NONE;
        };
    }

    public static String key(int id) {
        return switch (id) {
            case HIRO -> "hiro";
            case WEIJIE -> "weijie";
            case NOA -> "noa";
            default -> "";
        };
    }

    public static int resolveTags(Set<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return NONE;
        }
        // Explicit priority makes multiple tags deterministic across all clients.
        if (containsTag(tags, "hiro")) {
            return HIRO;
        }
        if (containsTag(tags, "weijie")) {
            return WEIJIE;
        }
        if (containsTag(tags, "noa")) {
            return NOA;
        }
        return NONE;
    }

    public static Identifier textureId(int id) {
        String key = key(id);
        return key.isEmpty() ? null
                : Identifier.of("monvhua", "textures/character_bubble/" + key + ".png");
    }

    public static boolean isPixel(int id) {
        return id == NOA;
    }

    private static boolean containsTag(Set<String> tags, String expected) {
        for (String tag : tags) {
            if (tag == null) {
                continue;
            }
            String normalized = tag.toLowerCase(Locale.ROOT);
            if (normalized.equals(expected) || normalized.equals("monvhua_avatar:" + expected)) {
                return true;
            }
        }
        return false;
    }
}
