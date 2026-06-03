package com.kuilunfuzhe.monvhua.features.block.body_skeletal;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

public class SkeletalLeftLegBlock extends SkeletalBodyPartBlock {
    public SkeletalLeftLegBlock(Settings settings) {
        super(settings, SkeletalBodyPart.LEFT_LEG);
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return createCodec(SkeletalLeftLegBlock::new);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new SkeletalLeftLegBlockEntity(pos, state);
    }
}
