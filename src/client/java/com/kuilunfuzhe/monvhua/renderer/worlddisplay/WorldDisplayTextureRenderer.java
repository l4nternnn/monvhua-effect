package com.kuilunfuzhe.monvhua.renderer.worlddisplay;

import com.kuilunfuzhe.monvhua.features.mirror.FramebufferOverride;
import com.kuilunfuzhe.monvhua.features.portal.client.PortalFramebufferTexture;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.RawProjectionMatrix;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.render.block.entity.model.ChestBlockModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.opengl.GlStateManager;

/** Renders world-display models into a camera-independent transparent texture. */
public final class WorldDisplayTextureRenderer {
    private static final int WIDTH = 256;
    private static final int HEIGHT = 108;
    private static final float ASPECT = WIDTH / (float) HEIGHT;
    private static final Identifier TEXTURE_ID = Identifier.of(
            "monvhua", "dynamic/world_display"
    );
    private static final RawProjectionMatrix PROJECTION =
            new RawProjectionMatrix("monvhua world display projection");
    private static final Map<Integer, WorldDisplayViewProfile> VIEW_PROFILES = new HashMap<>();

    static {
        VIEW_PROFILES.put(17, WorldDisplayViewProfile.CHEST);
    }

    private static SimpleFramebuffer framebuffer;
    private static PortalFramebufferTexture texture;
    private static BufferAllocator displayAllocator;
    private static VertexConsumerProvider.Immediate displayConsumers;
    private static ChestBlockModel chestModel;
    private static SpriteIdentifier chestSprite;

    private WorldDisplayTextureRenderer() {
    }

    public static Identifier textureId() {
        return TEXTURE_ID;
    }

    public static void render(int contentId, MinecraftClient client, float animationProgress) {
        WorldDisplayViewProfile view = VIEW_PROFILES.get(contentId);
        if (contentId == 17 && view != null) {
            renderChest(client, animationProgress, view);
        }
    }

    public static void registerViewProfile(int contentId, WorldDisplayViewProfile profile) {
        if (profile != null) {
            VIEW_PROFILES.put(contentId, profile);
        } else {
            VIEW_PROFILES.remove(contentId);
        }
    }

    public static void renderChest(MinecraftClient client, float openProgress) {
        renderChest(client, openProgress, WorldDisplayViewProfile.CHEST);
    }

    private static void renderChest(MinecraftClient client, float openProgress,
                                    WorldDisplayViewProfile view) {
        if (client == null || client.world == null) {
            return;
        }
        ensureResources(client);
        if (framebuffer == null) {
            return;
        }
        if (chestModel == null) {
            chestModel = new ChestBlockModel(
                    client.getLoadedEntityModels().getModelPart(EntityModelLayers.CHEST)
            );
            chestSprite = TexturedRenderLayers.CHEST;
        }

        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                framebuffer.getColorAttachment(),
                0x00000000,
                framebuffer.getDepthAttachment(),
                1.0
        );

        GpuTextureView previousColor = RenderSystem.outputColorTextureOverride;
        GpuTextureView previousDepth = RenderSystem.outputDepthTextureOverride;
        RenderSystem.backupProjectionMatrix();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        FramebufferOverride.setOverride(framebuffer);
        RenderSystem.outputColorTextureOverride = framebuffer.getColorAttachmentView();
        RenderSystem.outputDepthTextureOverride = framebuffer.getDepthAttachmentView();
        int previousWidth = Math.max(1, client.getFramebuffer().textureWidth);
        int previousHeight = Math.max(1, client.getFramebuffer().textureHeight);
        GlStateManager._viewport(0, 0, WIDTH, HEIGHT);

        try {
            Matrix4f projection = new Matrix4f().setOrtho(
                    -ASPECT, ASPECT, -1.0F, 1.0F, -10.0F, 10.0F
            );
            RenderSystem.setProjectionMatrix(PROJECTION.set(projection), ProjectionType.ORTHOGRAPHIC);

            chestModel.setLockAndLidPitch(openProgress);
            MatrixStack matrices = new MatrixStack();
            matrices.translate(view.offsetX(), view.offsetY(), 0.0F);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(view.roll()));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(view.yaw()));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(view.pitch()));
            matrices.scale(1.68F * view.scale(), 1.68F * view.scale(), 1.68F * view.scale());
            matrices.translate(-0.5F, -0.72F, -0.5F);

            RenderLayer layer = RenderLayer.getEntityCutoutNoCull(chestSprite.getAtlasId());
            chestModel.render(
                    matrices,
                    chestSprite.getVertexConsumer(displayConsumers, ignored -> layer),
                    0x00F000F0,
                    net.minecraft.client.render.OverlayTexture.DEFAULT_UV
            );
            displayConsumers.draw();
        } finally {
            GlStateManager._viewport(0, 0, previousWidth, previousHeight);
            modelView.popMatrix();
            RenderSystem.restoreProjectionMatrix();
            RenderSystem.outputColorTextureOverride = previousColor;
            RenderSystem.outputDepthTextureOverride = previousDepth;
            FramebufferOverride.clearOverride();
        }
    }

    public static void cleanup(MinecraftClient client) {
        if (texture != null && client != null) {
            client.getTextureManager().destroyTexture(TEXTURE_ID);
        }
        texture = null;
        if (framebuffer != null) {
            framebuffer.delete();
            framebuffer = null;
        }
        if (displayAllocator != null) {
            displayAllocator.close();
            displayAllocator = null;
            displayConsumers = null;
        }
        chestModel = null;
        chestSprite = null;
    }

    private static void ensureResources(MinecraftClient client) {
        if (framebuffer == null) {
            framebuffer = new SimpleFramebuffer("monvhua_world_display", WIDTH, HEIGHT, true);
        }
        if (texture == null) {
            texture = new PortalFramebufferTexture();
            texture.setFramebuffer(framebuffer);
            client.getTextureManager().registerTexture(TEXTURE_ID, texture);
        }
        if (displayConsumers == null) {
            displayAllocator = new BufferAllocator(256 * 1024);
            displayConsumers = VertexConsumerProvider.immediate(displayAllocator);
        }
    }
}
