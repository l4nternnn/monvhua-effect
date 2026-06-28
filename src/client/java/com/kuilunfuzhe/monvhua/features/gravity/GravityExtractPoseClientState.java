package com.kuilunfuzhe.monvhua.features.gravity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class GravityExtractPoseClientState {
    private static final Map<Integer, Integer> ACTIVE = new ConcurrentHashMap<>();

    private GravityExtractPoseClientState() {
    }

    public static void set(int entityId, int ticks) {
        if (ticks <= 0) {
            ACTIVE.remove(entityId);
        } else {
            ACTIVE.put(entityId, ticks);
        }
    }

    public static void tick() {
        ACTIVE.entrySet().removeIf(entry -> entry.getValue() <= 1);
        ACTIVE.replaceAll((id, ticks) -> ticks - 1);
    }

    public static boolean isActive(int entityId) {
        return ACTIVE.getOrDefault(entityId, 0) > 0;
    }
}
