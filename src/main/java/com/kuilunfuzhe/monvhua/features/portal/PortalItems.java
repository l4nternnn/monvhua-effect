package com.kuilunfuzhe.monvhua.features.portal;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public final class PortalItems {
    public static final Block PORTAL_BLOCK;
    public static final BlockItem PORTAL_ITEM;
    public static final Block FRAME_BLOCK;
    public static final BlockItem FRAME_ITEM;

    static {
        Identifier id = Identifier.of(MonvhuaMod.MOD_ID, "light_portal");
        RegistryKey<Block> blockKey = RegistryKey.of(RegistryKeys.BLOCK, id);
        RegistryKey<Item> itemKey = RegistryKey.of(RegistryKeys.ITEM, id);
        Identifier frameId = Identifier.of(MonvhuaMod.MOD_ID, "light_portal_frame");
        RegistryKey<Block> frameBlockKey = RegistryKey.of(RegistryKeys.BLOCK, frameId);
        RegistryKey<Item> frameItemKey = RegistryKey.of(RegistryKeys.ITEM, frameId);

        PORTAL_BLOCK = new PortalBlock(AbstractBlock.Settings.create()
                .registryKey(blockKey)
                .mapColor(MapColor.CYAN)
                .strength(1.5F, 6.0F)
                .luminance(state -> 9)
                .nonOpaque()
                .pistonBehavior(PistonBehavior.DESTROY));
        PORTAL_ITEM = new BlockItem(PORTAL_BLOCK, new Item.Settings()
                .registryKey(itemKey)
                .maxCount(16));
        FRAME_BLOCK = new PortalFrameBlock(AbstractBlock.Settings.create()
                .registryKey(frameBlockKey)
                .mapColor(MapColor.CYAN)
                .strength(3.0F, 9.0F)
                .requiresTool()
                .luminance(state -> 3)
                .pistonBehavior(PistonBehavior.BLOCK));
        FRAME_ITEM = new BlockItem(FRAME_BLOCK, new Item.Settings()
                .registryKey(frameItemKey)
                .maxCount(64));
    }

    private PortalItems() {
    }

    public static void initialize() {
        Identifier id = Identifier.of(MonvhuaMod.MOD_ID, "light_portal");
        Identifier frameId = Identifier.of(MonvhuaMod.MOD_ID, "light_portal_frame");
        Registry.register(Registries.BLOCK, id, PORTAL_BLOCK);
        Registry.register(Registries.ITEM, id, PORTAL_ITEM);
        Registry.register(Registries.BLOCK, frameId, FRAME_BLOCK);
        Registry.register(Registries.ITEM, frameId, FRAME_ITEM);
        PortalBlockEntities.initialize();
        PortalManager.initialize();

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> entries.add(FRAME_ITEM));
    }
}
