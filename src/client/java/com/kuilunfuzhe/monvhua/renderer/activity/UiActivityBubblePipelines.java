package com.kuilunfuzhe.monvhua.renderer.activity;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.kuilunfuzhe.monvhua.compat.ActivityIrisPipelineCompat;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

public final class UiActivityBubblePipelines {
    public static final RenderPipeline BUBBLE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_PROJECTION_FOG_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
                    .withLocation(Identifier.of(MonvhuaMod.MOD_ID, "pipeline/ui_activity_bubble"))
                    .withVertexShader(Identifier.of(MonvhuaMod.MOD_ID, "core/ui_activity_bubble"))
                    .withFragmentShader(Identifier.of(MonvhuaMod.MOD_ID, "core/ui_activity_bubble"))
                    .withSampler("Sampler0")
                    .withSampler("Sampler1")
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    private UiActivityBubblePipelines() {
    }

    public static void initialize() {
        if (FabricLoader.getInstance().isModLoaded("iris")) {
            ActivityIrisPipelineCompat.assignBubblePipeline(BUBBLE);
        }
    }
}
