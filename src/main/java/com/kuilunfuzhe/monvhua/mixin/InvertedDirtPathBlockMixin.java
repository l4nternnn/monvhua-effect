package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.gravity.InvertedBlockContext;
import net.minecraft.block.BlockState;
import net.minecraft.block.DirtPathBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DirtPathBlock.class)
public abstract class InvertedDirtPathBlockMixin {

    @Inject(method = "canPlaceAt", at = @At("HEAD"), cancellable = true)
    private void monvhua$invertedCanPlaceAt(BlockState state, WorldView world, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!InvertedBlockContext.shouldMirror(world, pos)) {
            return;
        }

        BlockState stateBelow = world.getBlockState(pos.down());
        if (!stateBelow.isSolid() || stateBelow.getBlock() instanceof FenceGateBlock) {
            cir.setReturnValue(true);
        } else {
            cir.setReturnValue(false);
        }
    }
}
