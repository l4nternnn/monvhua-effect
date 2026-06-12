package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.gravity.InvertedBlockContext;
import com.kuilunfuzhe.monvhua.features.gravity.InvertedSupportWorldView;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SnowBlock;
import net.minecraft.block.SnowyBlock;
import net.minecraft.block.SpreadableBlock;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.chunk.light.ChunkLightProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpreadableBlock.class)
public abstract class InvertedSpreadableBlockMixin {

    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void monvhua$invertedRandomTick(BlockState state, ServerWorld world, BlockPos pos, Random random, CallbackInfo ci) {
        if (!InvertedBlockContext.shouldMirror(world, pos)) {
            return;
        }

        if (canSurviveInverted(state, world, pos)) {
            if (world.getLightLevel(invertedSurfacePos(pos)) >= 9) {
                BlockState defaultState = ((SpreadableBlock) (Object) this).getDefaultState();
                for (int i = 0; i < 4; i++) {
                    BlockPos targetPos = pos.add(
                            random.nextInt(3) - 1,
                            random.nextInt(5) - 1,
                            random.nextInt(3) - 1
                    );
                    BlockState targetState = world.getBlockState(targetPos);
                    if (targetState.isOf(Blocks.DIRT) && canSpreadInverted(defaultState, world, targetPos)) {
                        world.setBlockState(targetPos, defaultState.with(
                                SnowyBlock.SNOWY,
                                isSnowInverted(world.getBlockState(invertedSurfacePos(targetPos)))
                        ));
                    }
                }
            }
        } else {
            world.setBlockState(pos, Blocks.DIRT.getDefaultState());
        }

        ci.cancel();
    }

    private static boolean canSurviveInverted(BlockState state, ServerWorld world, BlockPos pos) {
        BlockPos surfacePos = invertedSurfacePos(pos);
        BlockState surfaceState = world.getBlockState(surfacePos);
        if (surfaceState.isOf(Blocks.SNOW) && surfaceState.contains(SnowBlock.LAYERS) && surfaceState.get(SnowBlock.LAYERS) == 1) {
            return true;
        }
        if (surfaceState.getFluidState().getLevel() == 8) {
            return false;
        }
        return ChunkLightProvider.getRealisticOpacity(
                state,
                surfaceState,
                InvertedSupportWorldView.flipVertical(Direction.UP),
                surfaceState.getOpacity()
        ) < 15;
    }

    private static boolean canSpreadInverted(BlockState state, ServerWorld world, BlockPos pos) {
        return canSurviveInverted(state, world, pos) && !world.getFluidState(invertedSurfacePos(pos)).isIn(FluidTags.WATER);
    }

    private static boolean isSnowInverted(BlockState state) {
        return state.isOf(Blocks.SNOW) || state.isOf(Blocks.SNOW_BLOCK);
    }

    private static BlockPos invertedSurfacePos(BlockPos pos) {
        return InvertedSupportWorldView.offsetFromFlippedSurface(pos, Direction.UP);
    }
}
