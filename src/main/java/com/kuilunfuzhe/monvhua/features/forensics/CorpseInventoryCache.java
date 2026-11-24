package com.kuilunfuzhe.monvhua.features.forensics;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtList;
import java.util.*;

/** Deep snapshots detect in-place count/component changes made by vanilla containers or other mods. */
public final class CorpseInventoryCache {
    private record Snapshot(List<ItemStack> items, NbtList encoded) {}
    private static final Map<UUID, Snapshot> entries = new HashMap<>();
    public static boolean unchanged(UUID id, List<ItemStack> inventory, NbtList persisted) {
        var previous = entries.get(id);
        if (previous == null || !previous.encoded.equals(persisted) || previous.items.size() != inventory.size()) return false;
        for (int i=0; i<inventory.size(); i++) {
            var before=previous.items.get(i);var current=inventory.get(i);
            if (before.isEmpty() && current.isEmpty()) continue;
            // Persistence must compare every component. GeckoLib's vanilla comparison hook deliberately ignores its ID.
            if (before.getCount()!=current.getCount() || before.getItem()!=current.getItem()
                    || !before.getComponents().equals(current.getComponents())) return false;
        }
        return true;
    }
    public static void remember(UUID id, List<ItemStack> inventory, NbtList encoded) {
        entries.put(id, new Snapshot(inventory.stream().map(ItemStack::copy).toList(), encoded.copy()));
    }
    public static void forget(UUID id) { entries.remove(id); }
    public static void clear() { entries.clear(); }
}
