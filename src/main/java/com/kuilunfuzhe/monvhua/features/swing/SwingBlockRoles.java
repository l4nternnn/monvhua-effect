package com.kuilunfuzhe.monvhua.features.swing;

import net.minecraft.block.*;
import net.minecraft.registry.tag.BlockTags;

/** Shared role predicates for geometry-based swing assembly. */
public final class SwingBlockRoles {
    private SwingBlockRoles() {}
    public static boolean rope(BlockState state) {
        return primaryRope(state) || state.getBlock() instanceof FenceBlock
                || state.isOf(Blocks.IRON_BARS);
    }
    public static boolean primaryRope(BlockState state) { return state.isOf(Blocks.CHAIN) && state.get(ChainBlock.AXIS) == net.minecraft.util.math.Direction.Axis.Y; }
    public static boolean seat(BlockState state) {
        Block block = state.getBlock();
        return (block instanceof TrapdoorBlock && !state.get(TrapdoorBlock.OPEN)) || block instanceof SlabBlock || block instanceof StairsBlock
                || block instanceof CarpetBlock || state.isIn(BlockTags.PLANKS);
    }
    public static boolean backrest(BlockState state) {
        return seat(state) || state.getBlock() instanceof TrapdoorBlock || state.getBlock() instanceof FenceBlock;
    }
}
