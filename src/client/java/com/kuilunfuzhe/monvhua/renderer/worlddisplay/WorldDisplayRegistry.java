package com.kuilunfuzhe.monvhua.renderer.worlddisplay;

import java.util.HashMap;
import java.util.Map;

public final class WorldDisplayRegistry {
    private static final Map<Integer, WorldDisplay> DISPLAYS = new HashMap<>();

    static {
        DISPLAYS.put(17, new ChestWorldDisplay());
    }

    private WorldDisplayRegistry() {
    }

    public static boolean contains(int contentId) {
        return DISPLAYS.containsKey(contentId);
    }

    public static void render(int contentId, WorldDisplayContext context) {
        WorldDisplay display = DISPLAYS.get(contentId);
        if (display != null) {
            display.render(context);
        }
    }
}
