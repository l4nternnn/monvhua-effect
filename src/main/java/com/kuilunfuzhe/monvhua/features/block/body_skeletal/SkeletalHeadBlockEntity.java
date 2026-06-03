package com.kuilunfuzhe.monvhua.features.block.body_skeletal;

import com.kuilunfuzhe.monvhua.entity.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

public class SkeletalHeadBlockEntity extends SkeletalBodyPartBlockEntity {
    public SkeletalHeadBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SKELETAL_HEAD_BLOCK_ENTITY, pos, state, SkeletalBodyPart.HEAD);
    }
}
