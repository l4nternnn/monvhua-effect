package com.kuilunfuzhe.monvhua.features.block.body.torso;

import com.kuilunfuzhe.monvhua.entity.ModBlockEntities;
import com.kuilunfuzhe.monvhua.features.block.body.BodyPartBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

public class TorsoBlockEntity extends BodyPartBlockEntity {
    public TorsoBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TORSO_BLOCK_ENTITY, pos, state);
    }
}
