package com.kuilunfuzhe.monvhua.features.block_hole;

import com.kuilunfuzhe.monvhua.features.block_hole.BlockHoleBlockEntities;
import com.kuilunfuzhe.monvhua.renderer.block_hole_render.BlockHoleBlockEntityRenderer;
import com.kuilunfuzhe.monvhua.renderer.block_hole_render.BlockHoleRenderPipelines;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;

public final class BlockHoleClient {
    private static boolean registered;

    private BlockHoleClient() {
    }

    public static void register() {
        if (registered) {
            return;
        }

        registered = true;
        BlockHoleRenderPipelines.initialize();
        BlockEntityRendererFactories.register(
                BlockHoleBlockEntities.BLOCK_HOLE_BLOCK_ENTITY,
                context -> new BlockHoleBlockEntityRenderer()
        );
    }
}
