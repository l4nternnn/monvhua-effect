package com.kuilunfuzhe.monvhua.features.gravity;

import net.minecraft.block.BlockState;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

public final class InvertedBlockContext {
    private InvertedBlockContext() {
    }

    public static boolean shouldMirror(BlockView world, BlockPos pos) {
        if (!(world instanceof World realWorld)) {
            return false;
        }

        return shouldMirror(realWorld.getRegistryKey(), world, pos, world.getBlockState(pos));
    }

    public static boolean shouldMirror(RegistryKey<World> worldKey, BlockView blockView, BlockPos pos, BlockState state) {
        if (GravityMagic.isInInvertedArea(worldKey, pos.toCenterPos())) {
            return true;
        }

        BlockPos counterpart = connectedDoubleBlockPos(blockView, pos, state);
        return counterpart != null && GravityMagic.isInInvertedArea(worldKey, counterpart.toCenterPos());
    }

    public static BlockPos connectedDoubleBlockPos(BlockView world, BlockPos pos, BlockState state) {
        if (!state.contains(Properties.DOUBLE_BLOCK_HALF)) {
            return null;
        }

        DoubleBlockHalf half = state.get(Properties.DOUBLE_BLOCK_HALF);
        if (half == DoubleBlockHalf.LOWER) {
            BlockPos up = pos.up();
            if (isMatchingHalf(world, up, state, DoubleBlockHalf.UPPER)) {
                return up;
            }

            BlockPos down = pos.down();
            return isMatchingHalf(world, down, state, DoubleBlockHalf.UPPER) ? down : null;
        }

        BlockPos down = pos.down();
        if (isMatchingHalf(world, down, state, DoubleBlockHalf.LOWER)) {
            return down;
        }

        BlockPos up = pos.up();
        return isMatchingHalf(world, up, state, DoubleBlockHalf.LOWER) ? up : null;
    }

    public static boolean isMatchingHalf(BlockView world, BlockPos pos, BlockState state, DoubleBlockHalf half) {
        BlockState other = world.getBlockState(pos);
        return other.isOf(state.getBlock())
                && other.contains(Properties.DOUBLE_BLOCK_HALF)
                && other.get(Properties.DOUBLE_BLOCK_HALF) == half;
    }
}
