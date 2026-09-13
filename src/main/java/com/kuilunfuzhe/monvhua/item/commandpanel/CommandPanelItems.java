package com.kuilunfuzhe.monvhua.item.commandpanel;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public final class CommandPanelItems {
    public static final Item COMMAND_PANEL = register("command_panel");
    private CommandPanelItems() {}
    private static Item register(String name) {
        Identifier id = Identifier.of("monvhua", name);
        Item item = new CommandPanelItem(new Item.Settings().registryKey(RegistryKey.of(RegistryKeys.ITEM, id)).maxCount(1));
        return Registry.register(Registries.ITEM, id, item);
    }
    public static void initialize() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(COMMAND_PANEL));
    }
}
