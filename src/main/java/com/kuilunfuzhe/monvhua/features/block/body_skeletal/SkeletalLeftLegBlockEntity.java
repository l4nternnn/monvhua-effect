package com.kuilunfuzhe.monvhua.features.block.body_skeletal;

import com.kuilunfuzhe.monvhua.entity.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

public class SkeletalLeftLegBlockEntity extends SkeletalBodyPartBlockEntity {
    public SkeletalLeftLegBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SKELETAL_LEFT_LEG_BLOCK_ENTITY, pos, state, SkeletalBodyPart.LEFT_LEG);
    }
}
