package com.kuilunfuzhe.monvhua.features.block.body_skeletal;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

public class SkeletalTorsoBlock extends SkeletalBodyPartBlock {
    public SkeletalTorsoBlock(Settings settings) {
        super(settings, SkeletalBodyPart.TORSO);
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return createCodec(SkeletalTorsoBlock::new);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new SkeletalTorsoBlockEntity(pos, state);
    }
}
