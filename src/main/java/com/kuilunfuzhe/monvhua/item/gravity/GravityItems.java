package com.kuilunfuzhe.monvhua.item.gravity;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public final class GravityItems {
    public static final String GRAVITY_WAND_ID = "gravity_wand";
    public static GravityWandItem GRAVITY_WAND;

    private GravityItems() {
    }

    public static void initialize() {
        Identifier id = Identifier.of("monvhua", GRAVITY_WAND_ID);
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, id);
        GRAVITY_WAND = new GravityWandItem(new Item.Settings()
                .registryKey(key)
                .maxCount(1));

        Registry.register(Registries.ITEM, id, GRAVITY_WAND);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS)
                .register(entries -> entries.add(GRAVITY_WAND));
    }
}
