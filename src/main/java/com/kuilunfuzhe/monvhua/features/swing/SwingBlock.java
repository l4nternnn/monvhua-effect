package com.kuilunfuzhe.monvhua.features.swing;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

/** A captured block in coordinates relative to the swing pivot. */
public record SwingBlock(BlockPos localPos, BlockState state) {
    public SwingBlock {
        localPos = localPos.toImmutable();
    }
}
