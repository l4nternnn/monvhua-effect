package com.kuilunfuzhe.monvhua.features.block.body.head;

import com.kuilunfuzhe.monvhua.entity.ModBlockEntities;
import com.kuilunfuzhe.monvhua.features.block.body.BodyPartBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

public class HeadBlockEntity extends BodyPartBlockEntity {
    public HeadBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HEAD_BLOCK_ENTITY, pos, state);
    }
}
