package com.kuilunfuzhe.monvhua.features.block.body_skeletal;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

public class SkeletalLeftArmBlock extends SkeletalBodyPartBlock {
    public SkeletalLeftArmBlock(Settings settings) {
        super(settings, SkeletalBodyPart.LEFT_ARM);
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return createCodec(SkeletalLeftArmBlock::new);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new SkeletalLeftArmBlockEntity(pos, state);
    }
}
