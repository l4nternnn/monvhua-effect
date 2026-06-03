package com.kuilunfuzhe.monvhua.features.block.body_skeletal;

import com.kuilunfuzhe.monvhua.entity.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

public class SkeletalRightArmBlockEntity extends SkeletalBodyPartBlockEntity {
    public SkeletalRightArmBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SKELETAL_RIGHT_ARM_BLOCK_ENTITY, pos, state, SkeletalBodyPart.RIGHT_ARM);
    }
}
