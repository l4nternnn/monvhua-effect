package com.kuilunfuzhe.monvhua.features.portal.client;

import com.kuilunfuzhe.monvhua.compat.DhCompat;
import com.kuilunfuzhe.monvhua.compat.IrisMirrorCompat;
import com.kuilunfuzhe.monvhua.features.portal.PortalBlockEntity;
import com.kuilunfuzhe.monvhua.features.portal.PortalItems;
import com.kuilunfuzhe.monvhua.features.portal.PortalLinkData;
import com.kuilunfuzhe.monvhua.mixin.CameraAccessor;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.concurrent.atomic.AtomicBoolean;

public final class PortalFramebufferRenderer {
    private static final AtomicBoolean RENDERING = new AtomicBoolean(false);
    private static final int MAX_DISTANCE_SQUARED = 48 * 48;
    private static final int SCAN_RADIUS_XZ = 48;
    private static final int SCAN_RADIUS_Y = 16;
    private static final int UPDATE_INTERVAL_TICKS = 4;
    private static final Identifier TEXTURE_ID = Identifier.of("monvhua", "dynamic/light_portal_view");
    private static SimpleFramebuffer framebuffer;
    private static PortalFramebufferTexture texture;
    private static BlockPos renderedPortalPos;
    private static int lastRenderTick = -UPDATE_INTERVAL_TICKS;

    private PortalFramebufferRenderer() {
    }

    public static SimpleFramebuffer getFramebufferFor(BlockPos pos) {
        return !RENDERING.get() && pos != null && pos.equals(renderedPortalPos) ? framebuffer : null;
    }

    public static Identifier getTextureIdFor(BlockPos pos) {
        return getFramebufferFor(pos) == null ? null : TEXTURE_ID;
    }

    public static void renderNearestPortal(RenderTickCounter tickCounter, GpuBufferSlice fog, Vector4f fogColor,
                                           Camera mainCamera, Matrix4f positionMatrix, Matrix4f projectionMatrix) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) {
            return;
        }
        PortalBlockEntity portal = findNearestPortal(client, mainCamera.getPos());
        if (portal == null || portal.getLinkData() == null) {
            return;
        }
        if (!portal.isActive()) {
            return;
        }
        int tick = client.player.age;
        if (renderedPortalPos != null && renderedPortalPos.equals(portal.getPos()) && tick - lastRenderTick < UPDATE_INTERVAL_TICKS) {
            return;
        }
        if (RENDERING.getAndSet(true)) {
            return;
        }
        try {
            if (renderPortalView(client, portal, tickCounter, fog, fogColor, mainCamera, positionMatrix, projectionMatrix)) {
                renderedPortalPos = portal.getPos();
                lastRenderTick = tick;
            }
        } finally {
            RENDERING.set(false);
        }
    }

    private static PortalBlockEntity findNearestPortal(MinecraftClient client, Vec3d cameraPos) {
        BlockPos base = BlockPos.ofFloored(cameraPos);
        PortalBlockEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.iterate(base.add(-SCAN_RADIUS_XZ, -SCAN_RADIUS_Y, -SCAN_RADIUS_XZ), base.add(SCAN_RADIUS_XZ, SCAN_RADIUS_Y, SCAN_RADIUS_XZ))) {
            if (!client.world.getBlockState(pos).isOf(PortalItems.PORTAL_BLOCK)) {
                continue;
            }
            double distance = pos.getSquaredDistance(base);
            if (distance > MAX_DISTANCE_SQUARED || distance >= bestDistance) {
                continue;
            }
            BlockEntity blockEntity = client.world.getBlockEntity(pos);
            if (blockEntity instanceof PortalBlockEntity portal && portal.isController() && portal.isActive() && portal.getLinkData() != null) {
                best = portal;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static boolean renderPortalView(MinecraftClient client, PortalBlockEntity portal, RenderTickCounter tickCounter,
                                         GpuBufferSlice fog, Vector4f fogColor, Camera mainCamera,
                                         Matrix4f positionMatrix, Matrix4f projectionMatrix) {
        PortalLinkData link = portal.getLinkData();
        if (link == null) {
            return false;
        }
        BlockEntity targetBlockEntity = client.world.getBlockEntity(link.targetPos());
        if (!(targetBlockEntity instanceof PortalBlockEntity targetPortal) || !targetPortal.isController()) {
            return false;
        }

        int width = Math.max(1, Math.min(640, client.getFramebuffer().textureWidth / 4));
        int height = Math.max(1, Math.min(360, client.getFramebuffer().textureHeight / 4));
        framebuffer = resize(framebuffer, width, height);
        ensureTextureRegistered(client);

        Vec3d sourceCenter = portal.getPortalCenter();
        Vec3d targetCenter = targetPortal.getPortalCenter();
        Vec3d cameraRelative = mainCamera.getPos().subtract(sourceCenter);
        Vec3d cameraPos = targetCenter
                .add(rotateHorizontal(cameraRelative, portal.getFacing(), targetPortal.getFacing().getOpposite()))
                .add(normal(targetPortal.getFacing()).multiply(0.35D));
        float yawDelta = targetPortal.getFacing().getPositiveHorizontalDegrees()
                - portal.getFacing().getPositiveHorizontalDegrees()
                + 180.0F;
        float yaw = mainCamera.getYaw() + yawDelta;
        float pitch = mainCamera.getPitch();

        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                framebuffer.getColorAttachment(),
                0xFF000000,
                framebuffer.getDepthAttachment(),
                1.0
        );

        GpuTextureView previousColor = RenderSystem.outputColorTextureOverride;
        GpuTextureView previousDepth = RenderSystem.outputDepthTextureOverride;
        GpuBufferSlice previousFog = RenderSystem.getShaderFog();
        RenderSystem.backupProjectionMatrix();
        DhCompat.suspend();
        IrisMirrorCompat.beginMirrorRender();
        PortalFramebufferOverride.set(framebuffer);
        RenderSystem.outputColorTextureOverride = framebuffer.getColorAttachmentView();
        RenderSystem.outputDepthTextureOverride = framebuffer.getDepthAttachmentView();
        try {
            Camera portalCamera = new Camera();
            portalCamera.update(client.world, client.player, false, false, tickCounter.getTickProgress(false));
            CameraAccessor accessor = (CameraAccessor) portalCamera;
            accessor.invokeSetPos(cameraPos.x, cameraPos.y, cameraPos.z);
            accessor.invokeSetRotation(yaw, pitch);

            Matrix4f view = new Matrix4f(positionMatrix);
            Matrix4f projection = new Matrix4f(projectionMatrix);
            client.worldRenderer.setupFrustum(cameraPos, view, projection);
            client.worldRenderer.render(
                    ObjectAllocator.TRIVIAL,
                    tickCounter,
                    false,
                    portalCamera,
                    view,
                    projection,
                    fog,
                    fogColor,
                    true
            );
        } finally {
            client.worldRenderer.setupFrustum(mainCamera.getPos(), new Matrix4f(positionMatrix), new Matrix4f(projectionMatrix));
            RenderSystem.restoreProjectionMatrix();
            RenderSystem.setShaderFog(previousFog);
            RenderSystem.outputColorTextureOverride = previousColor;
            RenderSystem.outputDepthTextureOverride = previousDepth;
            PortalFramebufferOverride.clear();
            IrisMirrorCompat.endMirrorRender();
            DhCompat.resume();
        }
        return true;
    }

    private static SimpleFramebuffer resize(SimpleFramebuffer current, int width, int height) {
        if (current == null || current.textureWidth != width || current.textureHeight != height) {
            if (current != null) {
                current.delete();
            }
            SimpleFramebuffer next = new SimpleFramebuffer("monvhua_portal", width, height, true);
            if (texture != null) {
                texture.setFramebuffer(next);
            }
            return next;
        }
        return current;
    }

    private static void ensureTextureRegistered(MinecraftClient client) {
        if (texture != null) {
            texture.setFramebuffer(framebuffer);
            return;
        }
        texture = new PortalFramebufferTexture();
        texture.setFramebuffer(framebuffer);
        client.getTextureManager().registerTexture(TEXTURE_ID, texture);
    }

    private static Vec3d rotateHorizontal(Vec3d vector, Direction from, Direction to) {
        int steps = Math.floorMod(to.getHorizontalQuarterTurns() - from.getHorizontalQuarterTurns(), 4);
        return switch (steps) {
            case 1 -> new Vec3d(-vector.z, vector.y, vector.x);
            case 2 -> new Vec3d(-vector.x, vector.y, -vector.z);
            case 3 -> new Vec3d(vector.z, vector.y, -vector.x);
            default -> vector;
        };
    }

    private static Vec3d normal(Direction direction) {
        return new Vec3d(direction.getOffsetX(), direction.getOffsetY(), direction.getOffsetZ());
    }
}
