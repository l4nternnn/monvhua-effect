package com.kuilunfuzhe.monvhua.features.swing;

import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/** Finds swings by their complete moving structure instead of the pivot entity's section. */
public final class SwingSpatialIndex {
    private static final WeakHashMap<World, Set<SwingEntity>> BY_WORLD = new WeakHashMap<>();

    private SwingSpatialIndex() {
    }

    public static synchronized void register(SwingEntity swing) {
        BY_WORLD.computeIfAbsent(swing.getWorld(), ignored ->
                Collections.newSetFromMap(new WeakHashMap<>())).add(swing);
    }

    public static synchronized void unregister(SwingEntity swing) {
        Set<SwingEntity> swings = BY_WORLD.get(swing.getWorld());
        if (swings == null) return;
        swings.remove(swing);
        if (swings.isEmpty()) BY_WORLD.remove(swing.getWorld());
    }

    public static synchronized List<SwingEntity> find(World world, Box bounds) {
        Set<SwingEntity> swings = BY_WORLD.get(world);
        if (swings == null || swings.isEmpty()) return List.of();
        ArrayList<SwingEntity> result = new ArrayList<>();
        swings.removeIf(swing -> swing == null || swing.isRemoved());
        for (SwingEntity swing : swings) {
            if (swing.canHit() && swing.structureBounds().intersects(bounds)) result.add(swing);
        }
        return result;
    }
}
