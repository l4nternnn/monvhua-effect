package com.kuilunfuzhe.monvhua.features.portal;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class PortalFrameBlock extends Block {
    public PortalFrameBlock(AbstractBlock.Settings settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends Block> getCodec() {
        return createCodec(PortalFrameBlock::new);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }
        if (world instanceof ServerWorld serverWorld) {
            return PortalManager.tryCreateAt(serverWorld, pos, player) ? ActionResult.SUCCESS_SERVER : ActionResult.PASS;
        }
        return ActionResult.PASS;
    }

    @Override
    protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
        if (!moved) {
            PortalManager.onFrameRemoved(world, pos);
        }
        super.onStateReplaced(state, world, pos, moved);
    }
}
