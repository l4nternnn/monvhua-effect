package com.kuilunfuzhe.monvhua.features.paint;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.kuilunfuzhe.monvhua.item.paint.PaintItems;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class PaintBucketBlockEntities {
    public static final BlockEntityType<PaintBucketBlockEntity> PAINT_BUCKET_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(MonvhuaMod.MOD_ID, "paint_bucket"),
            FabricBlockEntityTypeBuilder.create(PaintBucketBlockEntity::new, PaintItems.PAINT_BUCKET_BLOCK).build()
    );

    private PaintBucketBlockEntities() {
    }

    public static void initialize() {
    }
}
