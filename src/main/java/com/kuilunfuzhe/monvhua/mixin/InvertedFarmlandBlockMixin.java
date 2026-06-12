package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.gravity.InvertedBlockContext;
import net.minecraft.block.BlockState;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.PistonExtensionBlock;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FarmlandBlock.class)
public abstract class InvertedFarmlandBlockMixin {

    @Inject(method = "canPlaceAt", at = @At("HEAD"), cancellable = true)
    private void monvhua$invertedCanPlaceAt(BlockState state, WorldView world, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!InvertedBlockContext.shouldMirror(world, pos)) {
            return;
        }

        BlockState stateBelow = world.getBlockState(pos.down());
        if (!stateBelow.isSolid()
                || stateBelow.getBlock() instanceof FenceGateBlock
                || stateBelow.getBlock() instanceof PistonExtensionBlock) {
            cir.setReturnValue(true);
        } else {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void monvhua$invertedRandomTick(BlockState state, ServerWorld world, BlockPos pos, Random random, CallbackInfo ci) {
        if (!InvertedBlockContext.shouldMirror(world, pos)) {
            return;
        }

        int moisture = state.get(FarmlandBlock.MOISTURE);

        if (monvhua$isWaterNearby(world, pos) || world.hasRain(pos.up())) {
            if (moisture < 7) {
                world.setBlockState(pos, state.with(FarmlandBlock.MOISTURE, 7), 2);
            }
        } else {
            if (moisture > 0) {
                world.setBlockState(pos, state.with(FarmlandBlock.MOISTURE, moisture - 1), 2);
            } else if (!monvhua$hasCropInverted(world, pos)) {
                FarmlandBlock.setToDirt(null, state, world, pos);
            }
        }

        ci.cancel();
    }

    @Unique
    private static boolean monvhua$hasCropInverted(BlockView world, BlockPos pos) {
        return world.getBlockState(pos.down()).isIn(BlockTags.MAINTAINS_FARMLAND);
    }

    @Unique
    private static boolean monvhua$isWaterNearby(WorldView world, BlockPos pos) {
        for (BlockPos nearbyPos : BlockPos.iterate(pos.add(-4, 0, -4), pos.add(4, 1, 4))) {
            if (world.getFluidState(nearbyPos).isIn(FluidTags.WATER)) {
                return true;
            }
        }
        return false;
    }
}
