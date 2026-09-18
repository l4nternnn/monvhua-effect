package com.kuilunfuzhe.monvhua.features.swing;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Immutable local world. No chunks, ticking, or reference to a live world are retained. */
public final class SwingBlockWorld implements BlockView {
    private final Map<BlockPos, BlockState> states;
    private final int bottom;
    private final int height;

    public SwingBlockWorld(List<SwingBlock> blocks) {
        Map<BlockPos, BlockState> captured = new HashMap<>();
        for (SwingBlock block : blocks) captured.put(block.localPos(), block.state());
        states = Map.copyOf(captured);
        bottom = blocks.stream().mapToInt(b -> b.localPos().getY()).min().orElse(0) - 1;
        height = blocks.stream().mapToInt(b -> b.localPos().getY()).max().orElse(0) - bottom + 2;
    }

    @Override public BlockState getBlockState(BlockPos pos) { return states.getOrDefault(pos, Blocks.AIR.getDefaultState()); }
    @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
    @Override public BlockEntity getBlockEntity(BlockPos pos) { return null; }
    @Override public int getBottomY() { return bottom; }
    @Override public int getHeight() { return height; }
}
