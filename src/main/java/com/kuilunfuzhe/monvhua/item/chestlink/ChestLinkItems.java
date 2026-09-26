package com.kuilunfuzhe.monvhua.item.chestlink;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import com.kuilunfuzhe.monvhua.features.chestlink.ChestLinkServer;

public final class ChestLinkItems {
    public static final ChestLinkItem CHEST_LINK;
    static {
        Identifier id = Identifier.of("monvhua", "chest_link");
        CHEST_LINK = new ChestLinkItem(new Item.Settings().registryKey(RegistryKey.of(RegistryKeys.ITEM, id)).maxCount(1));
    }
    public static void initialize() {
        Registry.register(Registries.ITEM, Identifier.of("monvhua", "chest_link"), CHEST_LINK);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(CHEST_LINK));
        ChestLinkItem.registerInteraction();
        ChestLinkServer.initialize();
    }
}
