package com.kuilunfuzhe.monvhua.renderer.block_hole_render;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

public final class BlockHoleRenderPipelines {
    public static final RenderPipeline BLOCK_HOLE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_PROJECTION_FOG_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
                    .withLocation(Identifier.of(MonvhuaMod.MOD_ID, "pipeline/block_hole"))
                    .withVertexShader(Identifier.of(MonvhuaMod.MOD_ID, "core/block_hole"))
                    .withFragmentShader(Identifier.of(MonvhuaMod.MOD_ID, "core/block_hole"))
                    .withSampler("Sampler0")
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE, VertexFormat.DrawMode.QUADS)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    private BlockHoleRenderPipelines() {
    }

    public static void initialize() {
    }
}
