package com.kuilunfuzhe.monvhua.features.paint;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

public final class PaintRenderPipelines {
    public static final RenderPipeline PAINT_OVERLAY = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of(MonvhuaMod.MOD_ID, "pipeline/paint_overlay"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    private PaintRenderPipelines() {
    }

    public static void initialize() {
    }
}
