package com.kuilunfuzhe.monvhua.item.swing;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.*;
import net.minecraft.util.Identifier;

public final class SwingAssemblyItems {
    public static final String ID = "assemble_stick";
    public static Item ASSEMBLE_STICK;
    private SwingAssemblyItems() {}
    public static void initialize() {
        Identifier id = Identifier.of("monvhua", ID);
        ASSEMBLE_STICK = Registry.register(Registries.ITEM, id,
                new Item(new Item.Settings().registryKey(RegistryKey.of(RegistryKeys.ITEM, id)).maxCount(1)));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(ASSEMBLE_STICK));
    }
}
