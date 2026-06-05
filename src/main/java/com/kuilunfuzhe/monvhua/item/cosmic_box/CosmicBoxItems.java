package com.kuilunfuzhe.monvhua.item.cosmic_box;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.kuilunfuzhe.monvhua.features.cosmic_box.CosmicBoxBlock;
import com.kuilunfuzhe.monvhua.features.cosmic_box.CosmicBoxBlockEntities;
import com.kuilunfuzhe.monvhua.features.cosmic_box.CosmicBoxVisualBlock;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public final class CosmicBoxItems {
    public static final Block COSMIC_BOX_BLOCK;
    public static final BlockItem COSMIC_BOX_ITEM;
    public static final Block COSMIC_BOX_RENDER_BLOCK;
    public static final BlockItem COSMIC_BOX_RENDER_ITEM;
    public static final Block COSMIC_BOX_DOOR_BLOCK;
    public static final BlockItem COSMIC_BOX_DOOR_ITEM;

    static {
        Identifier id = Identifier.of(MonvhuaMod.MOD_ID, "cosmic_box");
        RegistryKey<Block> blockKey = RegistryKey.of(RegistryKeys.BLOCK, id);
        RegistryKey<Item> itemKey = RegistryKey.of(RegistryKeys.ITEM, id);

        COSMIC_BOX_BLOCK = new CosmicBoxBlock(AbstractBlock.Settings.create()
                .registryKey(blockKey)
                .mapColor(MapColor.BLUE)
                .strength(2.0F, 6.0F)
                .luminance(state -> 8)
                .nonOpaque()
                .pistonBehavior(PistonBehavior.DESTROY));
        COSMIC_BOX_ITEM = new BlockItem(COSMIC_BOX_BLOCK, new Item.Settings()
                .registryKey(itemKey)
                .maxCount(64));

        Identifier renderId = Identifier.of(MonvhuaMod.MOD_ID, "cosmic_box_render");
        RegistryKey<Block> renderBlockKey = RegistryKey.of(RegistryKeys.BLOCK, renderId);
        RegistryKey<Item> renderItemKey = RegistryKey.of(RegistryKeys.ITEM, renderId);

        COSMIC_BOX_RENDER_BLOCK = new CosmicBoxVisualBlock(AbstractBlock.Settings.create()
                .registryKey(renderBlockKey)
                .mapColor(MapColor.BLUE)
                .strength(2.0F, 6.0F)
                .luminance(state -> 8)
                .nonOpaque()
                .pistonBehavior(PistonBehavior.DESTROY));
        COSMIC_BOX_RENDER_ITEM = new BlockItem(COSMIC_BOX_RENDER_BLOCK, new Item.Settings()
                .registryKey(renderItemKey)
                .maxCount(64));

        Identifier doorId = Identifier.of(MonvhuaMod.MOD_ID, "cosmic_box_door");
        RegistryKey<Block> doorBlockKey = RegistryKey.of(RegistryKeys.BLOCK, doorId);
        RegistryKey<Item> doorItemKey = RegistryKey.of(RegistryKeys.ITEM, doorId);

        COSMIC_BOX_DOOR_BLOCK = new CosmicBoxVisualBlock(AbstractBlock.Settings.create()
                .registryKey(doorBlockKey)
                .mapColor(MapColor.BLACK)
                .strength(2.0F, 6.0F)
                .luminance(state -> 8)
                .nonOpaque()
                .pistonBehavior(PistonBehavior.DESTROY));
        COSMIC_BOX_DOOR_ITEM = new BlockItem(COSMIC_BOX_DOOR_BLOCK, new Item.Settings()
                .registryKey(doorItemKey)
                .maxCount(64));
    }

    private CosmicBoxItems() {
    }

    public static void initialize() {
        Identifier id = Identifier.of(MonvhuaMod.MOD_ID, "cosmic_box");
        Registry.register(Registries.BLOCK, id, COSMIC_BOX_BLOCK);
        Registry.register(Registries.ITEM, id, COSMIC_BOX_ITEM);
        Identifier renderId = Identifier.of(MonvhuaMod.MOD_ID, "cosmic_box_render");
        Registry.register(Registries.BLOCK, renderId, COSMIC_BOX_RENDER_BLOCK);
        Registry.register(Registries.ITEM, renderId, COSMIC_BOX_RENDER_ITEM);
        Identifier doorId = Identifier.of(MonvhuaMod.MOD_ID, "cosmic_box_door");
        Registry.register(Registries.BLOCK, doorId, COSMIC_BOX_DOOR_BLOCK);
        Registry.register(Registries.ITEM, doorId, COSMIC_BOX_DOOR_ITEM);
        CosmicBoxBlockEntities.initialize();

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.BUILDING_BLOCKS).register(entries -> {
            entries.add(COSMIC_BOX_ITEM);
            entries.add(COSMIC_BOX_RENDER_ITEM);
            entries.add(COSMIC_BOX_DOOR_ITEM);
        });
    }
}
