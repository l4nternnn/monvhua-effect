package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.gravity.InvertedSupportWorldView;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Block.class)
public abstract class InvertedSupportSmallSquareMixin {
    @ModifyVariable(
            method = "sideCoversSmallSquare",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private static Direction monvhua$flipInvertedSmallSquareSide(Direction direction, WorldView world, BlockPos pos) {
        return InvertedSupportWorldView.flipDirection(world, direction);
    }
}
