package com.kuilunfuzhe.monvhua.features.swing;

import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

/** Finds swings by their complete moving structure instead of the pivot entity's section. */
public final class SwingSpatialIndex {
    private static final WeakHashMap<World, SwingSectionIndex<SwingEntity>> BY_WORLD = new WeakHashMap<>();

    private SwingSpatialIndex() {
    }

    public static synchronized void register(SwingEntity swing) {
        if (swing.isRemoved()) return;
        BY_WORLD.computeIfAbsent(swing.getWorld(), ignored -> new SwingSectionIndex<>()).update(swing, swing.structureBounds());
    }

    public static synchronized void unregister(SwingEntity swing) {
        SwingSectionIndex<SwingEntity> swings = BY_WORLD.get(swing.getWorld());
        if (swings == null) return;
        swings.remove(swing);
    }

    public static synchronized List<SwingEntity> find(World world, Box bounds) {
        SwingSectionIndex<SwingEntity> swings = BY_WORLD.get(world);
        if (swings == null) return List.of();
        ArrayList<SwingEntity> result = new ArrayList<>();
        for (SwingEntity swing : swings.query(bounds)) {
            if (swing.isRemoved()) { swings.remove(swing); continue; }
            if (swing.canHit() && swing.structureBounds().intersects(bounds)) result.add(swing);
        }
        return result;
    }
}
