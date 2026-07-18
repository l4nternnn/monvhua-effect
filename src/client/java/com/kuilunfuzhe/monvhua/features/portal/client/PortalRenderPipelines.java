package com.kuilunfuzhe.monvhua.features.portal.client;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

public final class PortalRenderPipelines {
    public static final RenderPipeline PORTAL_SURFACE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of(MonvhuaMod.MOD_ID, "pipeline/portal_surface"))
                    .withVertexShader(Identifier.of(MonvhuaMod.MOD_ID, "core/portal_atlas"))
                    .withFragmentShader(Identifier.of(MonvhuaMod.MOD_ID, "core/portal_atlas"))
                    .withSampler("Sampler0")
                    .withoutBlend()
                    .withDepthWrite(true)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                    .build()
    );

    private PortalRenderPipelines() {
    }

    public static RenderPipeline horizon() {
        return HorizonHolder.PORTAL_HORIZON;
    }

    public static RenderPipeline blockAtlas() {
        return BlockAtlasHolder.PORTAL_BLOCK_ATLAS;
    }

    public static RenderPipeline framebufferArea(boolean depthTest) {
        return depthTest
                ? FramebufferAreaHolder.PORTAL_FRAMEBUFFER_AREA
                : FramebufferAreaHolder.PORTAL_FRAMEBUFFER_AREA_NO_DEPTH;
    }

    private static final class BlockAtlasHolder {
        private static final RenderPipeline PORTAL_BLOCK_ATLAS = RenderPipelines.register(
                RenderPipeline.builder()
                        .withLocation(Identifier.of(MonvhuaMod.MOD_ID, "pipeline/portal_block_atlas"))
                        .withVertexShader(Identifier.of(MonvhuaMod.MOD_ID, "core/portal_atlas"))
                        .withFragmentShader(Identifier.of(MonvhuaMod.MOD_ID, "core/portal_atlas"))
                        .withSampler("Sampler0")
                        .withoutBlend()
                        .withDepthWrite(false)
                        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                        .withCull(false)
                        .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.TRIANGLES)
                        .build()
        );

        private BlockAtlasHolder() {
        }
    }

    private static final class FramebufferAreaHolder {
        private static final RenderPipeline PORTAL_FRAMEBUFFER_AREA = RenderPipelines.register(
                RenderPipeline.builder()
                        .withLocation(Identifier.of(MonvhuaMod.MOD_ID, "pipeline/portal_framebuffer_area"))
                        .withVertexShader(Identifier.of(MonvhuaMod.MOD_ID, "core/portal_framebuffer_area"))
                        .withFragmentShader(Identifier.of(MonvhuaMod.MOD_ID, "core/framebuffer_viewport"))
                        .withSampler("InSampler")
                        .withoutBlend()
                        .withDepthWrite(false)
                        .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                        .withCull(false)
                        .withVertexFormat(VertexFormats.POSITION_TEXTURE, VertexFormat.DrawMode.TRIANGLES)
                        .build()
        );
        private static final RenderPipeline PORTAL_FRAMEBUFFER_AREA_NO_DEPTH = RenderPipelines.register(
                RenderPipeline.builder()
                        .withLocation(Identifier.of(MonvhuaMod.MOD_ID, "pipeline/portal_framebuffer_area_no_depth"))
                        .withVertexShader(Identifier.of(MonvhuaMod.MOD_ID, "core/portal_framebuffer_area"))
                        .withFragmentShader(Identifier.of(MonvhuaMod.MOD_ID, "core/framebuffer_viewport"))
                        .withSampler("InSampler")
                        .withoutBlend()
                        .withDepthWrite(false)
                        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                        .withCull(false)
                        .withVertexFormat(VertexFormats.POSITION_TEXTURE, VertexFormat.DrawMode.TRIANGLES)
                        .build()
        );

        private FramebufferAreaHolder() {
        }
    }

    private static final class HorizonHolder {
        private static final RenderPipeline PORTAL_HORIZON = RenderPipelines.register(
                RenderPipeline.builder()
                        .withLocation(Identifier.of(MonvhuaMod.MOD_ID, "pipeline/portal_horizon"))
                        .withVertexShader(Identifier.of(MonvhuaMod.MOD_ID, "core/portal_horizon"))
                        .withFragmentShader(Identifier.of(MonvhuaMod.MOD_ID, "core/portal_horizon"))
                        .withoutBlend()
                        .withDepthWrite(false)
                        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                        .withCull(false)
                        .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
                        .build()
        );

        private HorizonHolder() {
        }
    }
}
