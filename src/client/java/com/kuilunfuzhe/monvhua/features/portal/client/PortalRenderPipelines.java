package com.kuilunfuzhe.monvhua.features.portal.client;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

public final class PortalRenderPipelines {
    private static final VertexFormatElement PORTAL_CLIP_W = registerPortalGenericFloatElement("clip W");
    private static final VertexFormatElement PORTAL_TEXTURE_W = registerPortalGenericFloatElement("texture W");
    private static final VertexFormat PORTAL_FRAMEBUFFER_AREA_FORMAT = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("ClipW", PORTAL_CLIP_W)
            .add("UV0", VertexFormatElement.UV0)
            .add("TextureW", PORTAL_TEXTURE_W)
            .build();

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

    public static RenderPipeline framebufferScreenArea(boolean depthTest) {
        return depthTest
                ? FramebufferScreenAreaHolder.PORTAL_FRAMEBUFFER_SCREEN_AREA
                : FramebufferScreenAreaHolder.PORTAL_FRAMEBUFFER_SCREEN_AREA_NO_DEPTH;
    }

    private static VertexFormatElement registerPortalGenericFloatElement(String name) {
        for (int id = 6; id < VertexFormatElement.MAX_COUNT; id++) {
            if (VertexFormatElement.byId(id) == null) {
                return VertexFormatElement.register(
                        id,
                        0,
                        VertexFormatElement.Type.FLOAT,
                        VertexFormatElement.Usage.GENERIC,
                        1
                );
            }
        }
        throw new IllegalStateException("No free vertex format element slot for portal " + name);
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
                        .withFragmentShader(Identifier.of(MonvhuaMod.MOD_ID, "core/portal_framebuffer_area"))
                        .withSampler("InSampler")
                        .withoutBlend()
                        .withDepthWrite(false)
                        .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                        .withCull(false)
                        .withVertexFormat(PORTAL_FRAMEBUFFER_AREA_FORMAT, VertexFormat.DrawMode.TRIANGLES)
                        .build()
        );
        private static final RenderPipeline PORTAL_FRAMEBUFFER_AREA_NO_DEPTH = RenderPipelines.register(
                RenderPipeline.builder()
                        .withLocation(Identifier.of(MonvhuaMod.MOD_ID, "pipeline/portal_framebuffer_area_no_depth"))
                        .withVertexShader(Identifier.of(MonvhuaMod.MOD_ID, "core/portal_framebuffer_area"))
                        .withFragmentShader(Identifier.of(MonvhuaMod.MOD_ID, "core/portal_framebuffer_area"))
                        .withSampler("InSampler")
                        .withoutBlend()
                        .withDepthWrite(false)
                        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                        .withCull(false)
                        .withVertexFormat(PORTAL_FRAMEBUFFER_AREA_FORMAT, VertexFormat.DrawMode.TRIANGLES)
                        .build()
        );

        private FramebufferAreaHolder() {
        }
    }

    private static final class FramebufferScreenAreaHolder {
        private static final RenderPipeline PORTAL_FRAMEBUFFER_SCREEN_AREA = RenderPipelines.register(
                RenderPipeline.builder()
                        .withLocation(Identifier.of(MonvhuaMod.MOD_ID, "pipeline/portal_framebuffer_screen_area"))
                        .withVertexShader(Identifier.of(MonvhuaMod.MOD_ID, "core/portal_framebuffer_screen_area"))
                        .withFragmentShader(Identifier.of(MonvhuaMod.MOD_ID, "core/portal_framebuffer_screen_area"))
                        .withSampler("InSampler")
                        .withoutBlend()
                        .withDepthWrite(false)
                        .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                        .withCull(false)
                        .withVertexFormat(PORTAL_FRAMEBUFFER_AREA_FORMAT, VertexFormat.DrawMode.TRIANGLES)
                        .build()
        );
        private static final RenderPipeline PORTAL_FRAMEBUFFER_SCREEN_AREA_NO_DEPTH = RenderPipelines.register(
                RenderPipeline.builder()
                        .withLocation(Identifier.of(MonvhuaMod.MOD_ID, "pipeline/portal_framebuffer_screen_area_no_depth"))
                        .withVertexShader(Identifier.of(MonvhuaMod.MOD_ID, "core/portal_framebuffer_screen_area"))
                        .withFragmentShader(Identifier.of(MonvhuaMod.MOD_ID, "core/portal_framebuffer_screen_area"))
                        .withSampler("InSampler")
                        .withoutBlend()
                        .withDepthWrite(false)
                        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                        .withCull(false)
                        .withVertexFormat(PORTAL_FRAMEBUFFER_AREA_FORMAT, VertexFormat.DrawMode.TRIANGLES)
                        .build()
        );

        private FramebufferScreenAreaHolder() {
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
