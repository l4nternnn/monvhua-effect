package com.kuilunfuzhe.monvhua.features.swing;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Registers every covered 16-block section, including those far below the entity pivot. */
final class SwingSectionIndex<T> {
    private final Map<T, Set<Long>> membership = new WeakHashMap<>();
    private final Map<Long, Set<T>> sections = new HashMap<>();

    void update(T value, Box bounds) {
        // Weak keys avoid retaining unloaded entities. Also discard vacated sections.
        sections.values().removeIf(Set::isEmpty);
        Set<Long> keys = keys(bounds);
        if (keys.equals(membership.get(value))) return;
        remove(value);
        membership.put(value, keys);
        for (long key : keys) sections.computeIfAbsent(key, ignored -> Collections.newSetFromMap(new WeakHashMap<>())).add(value);
    }

    void remove(T value) {
        Set<Long> old = membership.remove(value);
        if (old == null) return;
        for (long key : old) {
            Set<T> bucket = sections.get(key);
            if (bucket == null) continue;
            bucket.remove(value);
            if (bucket.isEmpty()) sections.remove(key);
        }
    }

    Set<T> query(Box bounds) {
        Set<T> found = new HashSet<>();
        for (long key : keys(bounds)) {
            Set<T> bucket = sections.get(key);
            if (bucket != null) {
                found.addAll(bucket);
                if (bucket.isEmpty()) sections.remove(key);
            }
        }
        return found;
    }

    private static Set<Long> keys(Box box) {
        Set<Long> keys = new HashSet<>();
        for (int x = MathHelper.floor(box.minX) >> 4; x <= MathHelper.floor(box.maxX) >> 4; x++)
            for (int y = MathHelper.floor(box.minY) >> 4; y <= MathHelper.floor(box.maxY) >> 4; y++)
                for (int z = MathHelper.floor(box.minZ) >> 4; z <= MathHelper.floor(box.maxZ) >> 4; z++)
                    keys.add(BlockPos.asLong(x, y, z));
        return keys;
    }
}
