package com.kuilunfuzhe.monvhua.item.block_hole;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.kuilunfuzhe.monvhua.features.block_hole.BlockHoleBlock;
import com.kuilunfuzhe.monvhua.features.block_hole.BlockHoleBlockEntities;
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

public final class BlockHoleItems {
    public static final Block BLOCK_HOLE_BLOCK;
    public static final BlockItem BLOCK_HOLE_ITEM;

    static {
        Identifier id = Identifier.of(MonvhuaMod.MOD_ID, "block_hole");
        RegistryKey<Block> blockKey = RegistryKey.of(RegistryKeys.BLOCK, id);
        RegistryKey<Item> itemKey = RegistryKey.of(RegistryKeys.ITEM, id);

        BLOCK_HOLE_BLOCK = new BlockHoleBlock(AbstractBlock.Settings.create()
                .registryKey(blockKey)
                .mapColor(MapColor.BLACK)
                .strength(2.0F, 6.0F)
                .luminance(state -> 6)
                .nonOpaque()
                .pistonBehavior(PistonBehavior.DESTROY));
        BLOCK_HOLE_ITEM = new BlockItem(BLOCK_HOLE_BLOCK, new Item.Settings()
                .registryKey(itemKey)
                .maxCount(64));
    }

    private BlockHoleItems() {
    }

    public static void initialize() {
        Identifier id = Identifier.of(MonvhuaMod.MOD_ID, "block_hole");
        Registry.register(Registries.BLOCK, id, BLOCK_HOLE_BLOCK);
        Registry.register(Registries.ITEM, id, BLOCK_HOLE_ITEM);
        BlockHoleBlockEntities.initialize();

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.BUILDING_BLOCKS).register(entries -> entries.add(BLOCK_HOLE_ITEM));
    }
}
