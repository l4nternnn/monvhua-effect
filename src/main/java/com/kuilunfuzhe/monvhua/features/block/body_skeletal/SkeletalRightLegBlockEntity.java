package com.kuilunfuzhe.monvhua.features.block.body_skeletal;

import com.kuilunfuzhe.monvhua.entity.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

public class SkeletalRightLegBlockEntity extends SkeletalBodyPartBlockEntity {
    public SkeletalRightLegBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SKELETAL_RIGHT_LEG_BLOCK_ENTITY, pos, state, SkeletalBodyPart.RIGHT_LEG);
    }
}
