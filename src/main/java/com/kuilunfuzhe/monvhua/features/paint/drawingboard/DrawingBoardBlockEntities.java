package com.kuilunfuzhe.monvhua.features.paint.drawingboard;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.kuilunfuzhe.monvhua.item.modblock.ModBlocks;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class DrawingBoardBlockEntities {
    public static final BlockEntityType<DrawingBoardBlockEntity> DRAWING_BOARD_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(MonvhuaMod.MOD_ID, "drawing_board"),
            FabricBlockEntityTypeBuilder.create(DrawingBoardBlockEntity::new, ModBlocks.DRAWING_BOARD).build()
    );

    private DrawingBoardBlockEntities() {
    }

    public static void initialize() {
    }
}
