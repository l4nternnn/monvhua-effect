package com.kuilunfuzhe.monvhua.features.block.body_skeletal;

import com.kuilunfuzhe.monvhua.entity.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

public class SkeletalLeftArmBlockEntity extends SkeletalBodyPartBlockEntity {
    public SkeletalLeftArmBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SKELETAL_LEFT_ARM_BLOCK_ENTITY, pos, state, SkeletalBodyPart.LEFT_ARM);
    }
}
