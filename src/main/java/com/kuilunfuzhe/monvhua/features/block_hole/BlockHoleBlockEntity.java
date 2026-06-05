package com.kuilunfuzhe.monvhua.features.block_hole;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;

public class BlockHoleBlockEntity extends BlockEntity {
    public BlockHoleBlockEntity(BlockPos pos, BlockState state) {
        super(BlockHoleBlockEntities.BLOCK_HOLE_BLOCK_ENTITY, pos, state);
    }
}
