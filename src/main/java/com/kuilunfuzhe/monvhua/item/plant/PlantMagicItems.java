package com.kuilunfuzhe.monvhua.item.plant;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public final class PlantMagicItems {
    public static final String PLANT_WAND_ID = "plant_wand";
    public static PlantMagicItem PLANT_WAND;

    private PlantMagicItems() {
    }

    public static void initialize() {
        Identifier id = Identifier.of("monvhua", PLANT_WAND_ID);
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, id);
        PLANT_WAND = new PlantMagicItem(new Item.Settings()
                .registryKey(key)
                .maxCount(1));

        Registry.register(Registries.ITEM, id, PLANT_WAND);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS)
                .register(entries -> entries.add(PLANT_WAND));
    }
}
