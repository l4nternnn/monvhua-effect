package com.kuilunfuzhe.monvhua.renderer.activity;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.kuilunfuzhe.monvhua.features.mirror.FramebufferOverride;
import com.kuilunfuzhe.monvhua.features.portal.client.PortalFramebufferTexture;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.RawProjectionMatrix;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Produces activity bubbles with their native shader off-screen. The final world quad then uses
 * an Iris-known entity render layer, preserving normal world depth testing.
 */
public final class UiActivityBubbleTextureRenderer {
    private static final int WIDTH = 512;
    private static final int HEIGHT = 346;
    private static final long UNUSED_SLOT_LIFETIME_MS = 15_000L;
    private static final RawProjectionMatrix PROJECTION =
            new RawProjectionMatrix("monvhua activity bubble texture projection");
    private static final Map<UUID, Slot> SLOTS = new HashMap<>();

    private UiActivityBubbleTextureRenderer() {
    }

    public static Identifier render(UUID owner, MinecraftClient client, Identifier contentTexture,
                                    float reveal, float phase, int contentId) {
        if (owner == null || client == null) {
            return null;
        }

        Slot slot = SLOTS.computeIfAbsent(owner, UiActivityBubbleTextureRenderer::createSlot);
        slot.ensureRegistered(client);
        if (slot.framebuffer == null || slot.textureId == null) {
            return null;
        }

        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                slot.framebuffer.getColorAttachment(),
                0x00000000,
                slot.framebuffer.getDepthAttachment(),
                1.0
        );

        GpuTextureView previousColor = RenderSystem.outputColorTextureOverride;
        GpuTextureView previousDepth = RenderSystem.outputDepthTextureOverride;
        RenderSystem.backupProjectionMatrix();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        Framebuffer previousFramebufferOverride = FramebufferOverride.getOverride();
        int previousWidth = Math.max(1, client.getFramebuffer().textureWidth);
        int previousHeight = Math.max(1, client.getFramebuffer().textureHeight);
        FramebufferOverride.setOverride(slot.framebuffer);
        RenderSystem.outputColorTextureOverride = slot.framebuffer.getColorAttachmentView();
        RenderSystem.outputDepthTextureOverride = slot.framebuffer.getDepthAttachmentView();
        GlStateManager._viewport(0, 0, WIDTH, HEIGHT);

        try {
            RenderSystem.setProjectionMatrix(
                    PROJECTION.set(new Matrix4f().setOrtho(-1.0F, 1.0F, -1.0F, 1.0F, -10.0F, 10.0F)),
                    ProjectionType.ORTHOGRAPHIC
            );
            MatrixStack matrices = new MatrixStack();
            VertexConsumer vertices = slot.consumers.getBuffer(
                    contentTexture == null
                            ? UiActivityBubbleRenderLayers.bubble()
                            : UiActivityBubbleRenderLayers.bubble(contentTexture)
            );
            int revealByte = Math.round(reveal * 255.0F);
            int phaseByte = Math.round(phase * 255.0F);
            int contentByte = Math.clamp(contentId, 0, 255);
            Matrix4f positionMatrix = matrices.peek().getPositionMatrix();
            emitBubbleVertex(vertices, positionMatrix, -1.0F, -1.0F, 0.0F, 0.0F,
                    revealByte, phaseByte, contentByte);
            emitBubbleVertex(vertices, positionMatrix, 1.0F, -1.0F, 1.0F, 0.0F,
                    revealByte, phaseByte, contentByte);
            emitBubbleVertex(vertices, positionMatrix, 1.0F, 1.0F, 1.0F, 1.0F,
                    revealByte, phaseByte, contentByte);
            emitBubbleVertex(vertices, positionMatrix, -1.0F, 1.0F, 0.0F, 1.0F,
                    revealByte, phaseByte, contentByte);
            slot.consumers.draw();
            slot.lastUsedMillis = Util.getMeasuringTimeMs();
            return slot.textureId;
        } finally {
            GlStateManager._viewport(0, 0, previousWidth, previousHeight);
            modelView.popMatrix();
            RenderSystem.restoreProjectionMatrix();
            RenderSystem.outputColorTextureOverride = previousColor;
            RenderSystem.outputDepthTextureOverride = previousDepth;
            if (previousFramebufferOverride == null) {
                FramebufferOverride.clearOverride();
            } else {
                FramebufferOverride.setOverride(previousFramebufferOverride);
            }
        }
    }

    /** Releases all per-player GPU resources, for example when leaving a world. */
    public static void clear() {
        for (Slot slot : SLOTS.values()) {
            slot.close(MinecraftClient.getInstance());
        }
        SLOTS.clear();
    }

    public static void trimInactive(MinecraftClient client, long nowMillis) {
        Iterator<Map.Entry<UUID, Slot>> iterator = SLOTS.entrySet().iterator();
        while (iterator.hasNext()) {
            Slot slot = iterator.next().getValue();
            if (nowMillis - slot.lastUsedMillis > UNUSED_SLOT_LIFETIME_MS) {
                slot.close(client);
                iterator.remove();
            }
        }
    }

    private static Slot createSlot(UUID owner) {
        return new Slot(Identifier.of(MonvhuaMod.MOD_ID, "dynamic/activity_bubble/" + owner));
    }

    private static void emitBubbleVertex(VertexConsumer vertices, Matrix4f matrix, float x, float y, float u, float v,
                                         int reveal, int phase, int contentId) {
        vertices.vertex(matrix, x, y, 0.0F)
                .texture(u, v)
                .color(reveal, phase, contentId, 255);
    }

    private static final class Slot {
        private final Identifier textureId;
        private SimpleFramebuffer framebuffer;
        private PortalFramebufferTexture texture;
        private BufferAllocator allocator;
        private VertexConsumerProvider.Immediate consumers;
        private long lastUsedMillis;
        private boolean registered;

        private Slot(Identifier textureId) {
            this.textureId = textureId;
            this.lastUsedMillis = Util.getMeasuringTimeMs();
        }

        private void ensureRegistered(MinecraftClient client) {
            if (framebuffer == null) {
                framebuffer = new SimpleFramebuffer("monvhua_activity_bubble", WIDTH, HEIGHT, true);
            }
            if (texture == null) {
                texture = new PortalFramebufferTexture();
                texture.setFramebuffer(framebuffer);
                texture.setLinearFiltering();
            }
            if (!registered) {
                client.getTextureManager().registerTexture(textureId, texture);
                registered = true;
            }
            if (consumers == null) {
                allocator = new BufferAllocator(64 * 1024);
                consumers = VertexConsumerProvider.immediate(allocator);
            }
        }

        private void close(MinecraftClient client) {
            if (registered && client != null) {
                client.getTextureManager().destroyTexture(textureId);
            }
            registered = false;
            texture = null;
            if (framebuffer != null) {
                framebuffer.delete();
                framebuffer = null;
            }
            if (allocator != null) {
                allocator.close();
                allocator = null;
                consumers = null;
            }
        }
    }
}
