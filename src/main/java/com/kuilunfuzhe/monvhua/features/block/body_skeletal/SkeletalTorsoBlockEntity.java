package com.kuilunfuzhe.monvhua.features.block.body_skeletal;

import com.kuilunfuzhe.monvhua.entity.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

public class SkeletalTorsoBlockEntity extends SkeletalBodyPartBlockEntity {
    public SkeletalTorsoBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SKELETAL_TORSO_BLOCK_ENTITY, pos, state, SkeletalBodyPart.TORSO);
    }
}
