package com.kuilunfuzhe.monvhua.item.paint;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.kuilunfuzhe.monvhua.features.paint.PaintBucketBlock;
import com.kuilunfuzhe.monvhua.features.paint.PaintBucketBlockEntities;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public final class PaintItems {
    public static final PaintBrushItem PAINT_BRUSH;
    public static final PaintBrushItem PAINT_SPRAY_CAN;
    public static final PaintEraserItem ERASER;
    public static final PaintPaperItem PAINT_PAPER;
    public static final Block PAINT_BUCKET_BLOCK;
    public static final PaintBucketItem PAINT_BUCKET;

    static {
        Identifier brushId = Identifier.of(MonvhuaMod.MOD_ID, "paint_brush");
        RegistryKey<Item> brushKey = RegistryKey.of(RegistryKeys.ITEM, brushId);
        PAINT_BRUSH = new PaintBrushItem(new Item.Settings()
                .registryKey(brushKey)
                .maxCount(1));

        Identifier sprayCanId = Identifier.of(MonvhuaMod.MOD_ID, "paint_spray_can");
        RegistryKey<Item> sprayCanKey = RegistryKey.of(RegistryKeys.ITEM, sprayCanId);
        PAINT_SPRAY_CAN = new PaintBrushItem(new Item.Settings()
                .registryKey(sprayCanKey)
                .maxCount(1));

        Identifier eraserId = Identifier.of(MonvhuaMod.MOD_ID, "eraser");
        RegistryKey<Item> eraserKey = RegistryKey.of(RegistryKeys.ITEM, eraserId);
        ERASER = new PaintEraserItem(new Item.Settings()
                .registryKey(eraserKey)
                .maxCount(1));

        Identifier paperId = Identifier.of(MonvhuaMod.MOD_ID, "paint_paper");
        RegistryKey<Item> paperKey = RegistryKey.of(RegistryKeys.ITEM, paperId);
        PAINT_PAPER = new PaintPaperItem(new Item.Settings()
                .registryKey(paperKey)
                .maxCount(1)
                .component(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true));

        Identifier bucketId = Identifier.of(MonvhuaMod.MOD_ID, "paint_bucket");
        RegistryKey<Block> bucketBlockKey = RegistryKey.of(RegistryKeys.BLOCK, bucketId);
        RegistryKey<Item> bucketKey = RegistryKey.of(RegistryKeys.ITEM, bucketId);
        PAINT_BUCKET_BLOCK = new PaintBucketBlock(AbstractBlock.Settings.create()
                .registryKey(bucketBlockKey)
                .mapColor(MapColor.IRON_GRAY)
                .strength(1.5F, 3.0F)
                .nonOpaque()
                .pistonBehavior(PistonBehavior.DESTROY));
        PAINT_BUCKET = new PaintBucketItem(PAINT_BUCKET_BLOCK, new Item.Settings()
                .registryKey(bucketKey)
                .maxCount(1));
    }

    private PaintItems() {
    }

    public static void initialize() {
        Registry.register(Registries.ITEM, Identifier.of(MonvhuaMod.MOD_ID, "paint_brush"), PAINT_BRUSH);
        Registry.register(Registries.ITEM, Identifier.of(MonvhuaMod.MOD_ID, "paint_spray_can"), PAINT_SPRAY_CAN);
        Registry.register(Registries.ITEM, Identifier.of(MonvhuaMod.MOD_ID, "eraser"), ERASER);
        Registry.register(Registries.ITEM, Identifier.of(MonvhuaMod.MOD_ID, "paint_paper"), PAINT_PAPER);
        Registry.register(Registries.BLOCK, Identifier.of(MonvhuaMod.MOD_ID, "paint_bucket"), PAINT_BUCKET_BLOCK);
        Registry.register(Registries.ITEM, Identifier.of(MonvhuaMod.MOD_ID, "paint_bucket"), PAINT_BUCKET);
        PaintBucketBlockEntities.initialize();
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
            entries.add(PAINT_BRUSH);
            entries.add(PAINT_SPRAY_CAN);
            entries.add(ERASER);
            entries.add(PAINT_PAPER);
            entries.add(PAINT_BUCKET);
        });
    }
}
