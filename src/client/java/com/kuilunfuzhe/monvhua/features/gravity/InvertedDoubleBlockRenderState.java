package com.kuilunfuzhe.monvhua.features.gravity;

import net.minecraft.block.BlockState;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

public final class InvertedDoubleBlockRenderState {
    private static final ThreadLocal<ModelState> MODEL_STATE = new ThreadLocal<>();

    private InvertedDoubleBlockRenderState() {
    }

    public static void prepareModelState(RegistryKey<World> worldKey, BlockView world, BlockPos pos, BlockState state) {
        BlockState replacement = replacementState(worldKey, world, pos, state);
        if (replacement == state) {
            MODEL_STATE.remove();
            return;
        }
        MODEL_STATE.set(new ModelState(state, replacement));
    }

    public static BlockState consumeModelState(BlockState requested) {
        ModelState modelState = MODEL_STATE.get();
        MODEL_STATE.remove();
        if (modelState == null || modelState.original != requested) {
            return requested;
        }
        return modelState.replacement;
    }

    public static BlockState replacementState(RegistryKey<World> worldKey, BlockView world, BlockPos pos, BlockState state) {
        if (!state.contains(Properties.DOUBLE_BLOCK_HALF)
                || !(state.getBlock() instanceof TallPlantBlock)
                || !InvertedBlockContext.shouldMirror(worldKey, world, pos, state)
                || hasInvertedCounterpart(world, pos, state)
                || !hasNormalCounterpart(world, pos, state)) {
            return state;
        }

        DoubleBlockHalf half = state.get(Properties.DOUBLE_BLOCK_HALF);
        return state.with(Properties.DOUBLE_BLOCK_HALF, half == DoubleBlockHalf.LOWER ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER);
    }

    public static boolean hasNormalCounterpart(BlockView world, BlockPos pos, BlockState state) {
        if (!state.contains(Properties.DOUBLE_BLOCK_HALF)) {
            return false;
        }

        DoubleBlockHalf half = state.get(Properties.DOUBLE_BLOCK_HALF);
        return half == DoubleBlockHalf.LOWER
                ? InvertedBlockContext.isMatchingHalf(world, pos.up(), state, DoubleBlockHalf.UPPER)
                : InvertedBlockContext.isMatchingHalf(world, pos.down(), state, DoubleBlockHalf.LOWER);
    }

    public static boolean hasInvertedCounterpart(BlockView world, BlockPos pos, BlockState state) {
        if (!state.contains(Properties.DOUBLE_BLOCK_HALF)) {
            return false;
        }

        DoubleBlockHalf half = state.get(Properties.DOUBLE_BLOCK_HALF);
        return half == DoubleBlockHalf.LOWER
                ? InvertedBlockContext.isMatchingHalf(world, pos.down(), state, DoubleBlockHalf.UPPER)
                : InvertedBlockContext.isMatchingHalf(world, pos.up(), state, DoubleBlockHalf.LOWER);
    }

    private record ModelState(BlockState original, BlockState replacement) {
    }
}
