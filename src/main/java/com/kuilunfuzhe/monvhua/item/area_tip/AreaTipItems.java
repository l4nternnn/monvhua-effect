package com.kuilunfuzhe.monvhua.item.area_tip;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public final class AreaTipItems {
    public static final String AREA_TIP_STICK_ID = "area_tip_stick";
    public static AreaTipStickItem AREA_TIP_STICK;

    private AreaTipItems() {
    }

    public static void initialize() {
        Identifier id = Identifier.of("monvhua", AREA_TIP_STICK_ID);
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, id);
        AREA_TIP_STICK = new AreaTipStickItem(new Item.Settings()
                .registryKey(key)
                .maxCount(1));
        Registry.register(Registries.ITEM, id, AREA_TIP_STICK);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS)
                .register(entries -> entries.add(AREA_TIP_STICK));
    }
}
