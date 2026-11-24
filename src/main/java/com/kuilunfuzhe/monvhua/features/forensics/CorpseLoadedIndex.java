package com.kuilunfuzhe.monvhua.features.forensics;

import com.kuilunfuzhe.monvhua.features.block.body.BodyPartManager;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import java.util.*;

/** Loaded displays only. Maintained by server entity lifecycle, never loads a chunk to find a UUID. */
public final class CorpseLoadedIndex {
    private static final Map<UUID, ItemDisplayEntity> loaded = new HashMap<>();
    public static void track(ItemDisplayEntity display) {
        if (!display.isRemoved() && (Forensics.hasBinding(display.getItemStack())
                || BodyPartManager.BODY_PART_DISPLAY_ITEMS.contains(display.getItemStack().getItem())
                || net.minecraft.util.Identifier.of("monvhua", "combined_body").equals(display.getItemStack().get(net.minecraft.component.DataComponentTypes.ITEM_MODEL))
                || BodyPartManager.DISPLAY_TO_INTERACTION.containsKey(display.getUuid()))) loaded.put(display.getUuid(), display);
    }
    public static void unload(ItemDisplayEntity display) {
        if (loaded.remove(display.getUuid(), display)) {
            CorpseGeometrySync.forget(display.getUuid());
            CorpseInventoryCache.forget(display.getUuid());
        }
    }
    public static List<ItemDisplayEntity> snapshot() { return List.copyOf(loaded.values()); }
    public static int size() { return loaded.size(); }
    public static String status() {
        var dimensions = new TreeMap<String,Integer>();
        int linked=0, inventories=0;
        for (var display : loaded.values()) {
            dimensions.merge(display.getWorld().getRegistryKey().getValue().toString(),1,Integer::sum);
            if (BodyPartManager.DISPLAY_TO_INTERACTION.containsKey(display.getUuid())) linked++;
            if (BodyPartManager.BODY_PART_DISPLAY_INVENTORIES.containsKey(display.getUuid())) inventories++;
        }
        return "loaded="+loaded.size()+" linked="+linked+" inventories="+inventories+" dimensions="+dimensions;
    }
    public static void clear() { loaded.clear(); CorpseGeometrySync.clear(); CorpseInventoryCache.clear(); CorpsePerformance.reset(); }
}
