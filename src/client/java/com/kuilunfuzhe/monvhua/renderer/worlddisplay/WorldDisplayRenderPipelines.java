package com.kuilunfuzhe.monvhua.renderer.worlddisplay;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

final class WorldDisplayRenderPipelines {
    static final RenderPipeline CHEST = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_PROJECTION_FOG_SNIPPET,
                            RenderPipelines.GLOBALS_SNIPPET)
                    .withLocation(Identifier.of(MonvhuaMod.MOD_ID, "pipeline/world_display_chest"))
                    .withVertexShader(Identifier.ofVanilla("core/entity"))
                    .withFragmentShader(Identifier.ofVanilla("core/entity"))
                    .withSampler("Sampler0")
                    .withoutBlend()
                    .withDepthWrite(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL,
                            VertexFormat.DrawMode.QUADS)
                    .build()
    );

    private WorldDisplayRenderPipelines() {
    }
}
