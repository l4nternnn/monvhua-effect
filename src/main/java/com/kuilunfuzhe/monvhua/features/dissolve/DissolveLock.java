package com.kuilunfuzhe.monvhua.features.dissolve;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DissolveLock {
    private static final Set<UUID> LOCKED_TARGETS = ConcurrentHashMap.newKeySet();

    private DissolveLock() {
    }

    public static void lock(UUID uuid) {
        if (uuid != null) {
            LOCKED_TARGETS.add(uuid);
        }
    }

    public static void unlock(UUID uuid) {
        if (uuid != null) {
            LOCKED_TARGETS.remove(uuid);
        }
    }

    public static boolean isLocked(UUID uuid) {
        return uuid != null && LOCKED_TARGETS.contains(uuid);
    }

    public static boolean canPaint(UUID uuid) {
        return !isLocked(uuid);
    }
}
