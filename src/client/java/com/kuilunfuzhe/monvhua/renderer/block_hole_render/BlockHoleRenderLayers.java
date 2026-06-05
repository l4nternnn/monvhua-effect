package com.kuilunfuzhe.monvhua.renderer.block_hole_render;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.util.Identifier;

public final class BlockHoleRenderLayers {
    private static final Identifier LENS_TEXTURE = Identifier.of(MonvhuaMod.MOD_ID, "textures/block/shader_use3.png");

    private static final RenderLayer BLOCK_HOLE = RenderLayer.of(
            "monvhua_block_hole",
            RenderLayer.DEFAULT_BUFFER_SIZE,
            false,
            true,
            BlockHoleRenderPipelines.BLOCK_HOLE,
            RenderLayer.MultiPhaseParameters.builder()
                    .texture(RenderPhase.Textures.create().add(LENS_TEXTURE, false).build())
                    .build(false)
    );

    private BlockHoleRenderLayers() {
    }

    public static RenderLayer blockHole() {
        return BLOCK_HOLE;
    }
}
