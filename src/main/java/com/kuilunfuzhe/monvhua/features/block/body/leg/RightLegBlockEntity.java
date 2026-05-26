package com.kuilunfuzhe.monvhua.features.block.body.leg;

import com.kuilunfuzhe.monvhua.entity.ModBlockEntities;
import com.kuilunfuzhe.monvhua.features.block.body.BodyPartBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

public class RightLegBlockEntity extends BodyPartBlockEntity{
    public RightLegBlockEntity(BlockPos pos, BlockState state) {
            super(ModBlockEntities.RIGHT_LEG_BLOCK_ENTITY, pos, state);
        }
}

