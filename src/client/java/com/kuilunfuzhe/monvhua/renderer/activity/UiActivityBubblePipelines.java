package com.kuilunfuzhe.monvhua.renderer.activity;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
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
                    // The FBO stores straight-alpha pixels. Blending here would premultiply RGB,
                    // then the outer entity layer would apply alpha a second time.
                    .withoutBlend()
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    /**
     * Final overlay pass. It is deliberately a standalone pipeline so Iris never
     * classifies the bubble as an entity/translucent material and feeds it into bloom.
     */
    public static final RenderPipeline COMPOSITE = RenderPipelines.register(
            RenderPipeline.builder()
                    .withLocation(Identifier.of(MonvhuaMod.MOD_ID, "pipeline/ui_activity_bubble_composite"))
                    .withVertexShader(Identifier.of(MonvhuaMod.MOD_ID, "core/ui_activity_bubble_composite"))
                    .withFragmentShader(Identifier.of(MonvhuaMod.MOD_ID, "core/ui_activity_bubble_composite"))
                    .withSampler("InSampler")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthWrite(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.TRIANGLES)
                    .build()
    );

    public static final RenderPipeline AVATAR_COMPOSITE = RenderPipelines.register(
            RenderPipeline.builder()
                    .withLocation(Identifier.of(MonvhuaMod.MOD_ID, "pipeline/ui_activity_avatar_composite"))
                    .withVertexShader(Identifier.of(MonvhuaMod.MOD_ID, "core/ui_activity_avatar_composite"))
                    .withFragmentShader(Identifier.of(MonvhuaMod.MOD_ID, "core/ui_activity_avatar_composite"))
                    .withSampler("InSampler")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthWrite(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.TRIANGLES)
                    .build()
    );
    private UiActivityBubblePipelines() {
    }
}
