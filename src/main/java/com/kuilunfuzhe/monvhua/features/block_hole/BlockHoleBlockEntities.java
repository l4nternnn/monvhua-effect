package com.kuilunfuzhe.monvhua.features.block_hole;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.kuilunfuzhe.monvhua.item.block_hole.BlockHoleItems;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class BlockHoleBlockEntities {
    public static final BlockEntityType<BlockHoleBlockEntity> BLOCK_HOLE_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(MonvhuaMod.MOD_ID, "block_hole"),
            FabricBlockEntityTypeBuilder.create(BlockHoleBlockEntity::new, BlockHoleItems.BLOCK_HOLE_BLOCK).build()
    );

    private BlockHoleBlockEntities() {
    }

    public static void initialize() {
    }
}
