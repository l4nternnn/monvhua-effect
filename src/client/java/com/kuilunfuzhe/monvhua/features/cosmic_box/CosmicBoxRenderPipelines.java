package com.kuilunfuzhe.monvhua.features.cosmic_box;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

public final class CosmicBoxRenderPipelines {
    public static final RenderPipeline COSMIC_BOX = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_PROJECTION_FOG_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
                    .withLocation(Identifier.of(MonvhuaMod.MOD_ID, "pipeline/cosmic_box"))
                    .withVertexShader(Identifier.of(MonvhuaMod.MOD_ID, "core/cosmic_box"))
                    .withFragmentShader(Identifier.of(MonvhuaMod.MOD_ID, "core/cosmic_box"))
                    .withSampler("Sampler0")
                    .withSampler("Sampler1")
                    .withShaderDefine("COSMIC_LAYERS", 9)
                    .withVertexFormat(VertexFormats.POSITION, VertexFormat.DrawMode.QUADS)
                    .withCull(false)
                    .build()
    );
    public static final RenderPipeline COSMIC_BEAM_RAINBOW = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_PROJECTION_FOG_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
                    .withLocation(Identifier.of(MonvhuaMod.MOD_ID, "pipeline/cosmic_beam_rainbow"))
                    .withVertexShader(Identifier.of(MonvhuaMod.MOD_ID, "core/cosmic_beam"))
                    .withFragmentShader(Identifier.of(MonvhuaMod.MOD_ID, "core/cosmic_beam"))
                    .withVertexFormat(VertexFormats.POSITION, VertexFormat.DrawMode.QUADS)
                    .withCull(false)
                    .build()
    );
    public static final RenderPipeline COSMIC_BEAM_WHITE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_PROJECTION_FOG_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
                    .withLocation(Identifier.of(MonvhuaMod.MOD_ID, "pipeline/cosmic_beam_white"))
                    .withVertexShader(Identifier.of(MonvhuaMod.MOD_ID, "core/cosmic_beam"))
                    .withFragmentShader(Identifier.of(MonvhuaMod.MOD_ID, "core/cosmic_beam_white"))
                    .withVertexFormat(VertexFormats.POSITION, VertexFormat.DrawMode.QUADS)
                    .withCull(false)
                    .build()
    );
    private CosmicBoxRenderPipelines() {
    }

    public static void initialize() {
    }
}
