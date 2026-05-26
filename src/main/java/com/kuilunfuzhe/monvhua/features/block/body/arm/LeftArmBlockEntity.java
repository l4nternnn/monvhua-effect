package com.kuilunfuzhe.monvhua.features.block.body.arm;

import com.kuilunfuzhe.monvhua.entity.ModBlockEntities;
import com.kuilunfuzhe.monvhua.features.block.body.BodyPartBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

public class LeftArmBlockEntity extends BodyPartBlockEntity {
    public LeftArmBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LEFT_ARM_BLOCK_ENTITY, pos, state);
    }
}