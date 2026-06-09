package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.gravity.InvertedSupportWorldView;
import net.minecraft.block.AbstractBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class InvertedSupportSideShapeMixin {
    @ModifyVariable(
            method = "isSideSolid",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private Direction monvhua$flipInvertedSupportSide(Direction direction, BlockView world, BlockPos pos) {
        return InvertedSupportWorldView.flipDirection(world, direction);
    }
}
