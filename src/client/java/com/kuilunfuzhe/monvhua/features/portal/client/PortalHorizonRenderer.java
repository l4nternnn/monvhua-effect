package com.kuilunfuzhe.monvhua.features.portal.client;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.kuilunfuzhe.monvhua.features.portal.PortalViewConfig;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public final class PortalHorizonRenderer {
    private static GpuBuffer horizonBuffer;
    private static int horizonKey;
    private static long lastLogTimeMs;

    private PortalHorizonRenderer() {
    }

    public static void render(SimpleFramebuffer targetFramebuffer, double cameraY, float pitch) {
        render(targetFramebuffer, null, null, null, cameraY, pitch);
    }

    public static void render(SimpleFramebuffer targetFramebuffer, Vec3d cameraPos,
                              Matrix4f view, Matrix4f projection, double cameraY, float pitch) {
        if (targetFramebuffer == null || targetFramebuffer.getColorAttachmentView() == null) {
            return;
        }
        PortalHorizonCache.HorizonData data = PortalHorizonCache.current();
        if ((data == null || data.sampleCount() <= 0) && !PortalViewConfig.PORTAL_HORIZON_FORCE_VISIBLE) {
            logMissingData();
            return;
        }

        int skyColor = data == null ? 0xFF78A7FF : data.skyColor();
        int fogColor = data == null ? 0xFFC8D8EA : data.fogColor();
        int groundColor = data == null ? 0xFF5F8F4E : data.averageGroundColor();
        float cameraHeight = data == null ? 0.55F : data.normalizedCameraHeight(cameraY);
        float pitchOffset = MathHelper.clamp(pitch / 90.0F, -1.0F, 1.0F) * 0.42F;
        float heightOffset = MathHelper.clamp((cameraHeight - 0.55F) * 0.26F, -0.18F, 0.18F);
        float horizonY = MathHelper.clamp(0.50F + pitchOffset + heightOffset, 0.18F, 0.82F);

        GpuBuffer vertexBuffer = data == null || view == null || projection == null || cameraPos == null
                ? getHorizonBuffer(skyColor, fogColor, groundColor, horizonY)
                : getProjectedTerrainBuffer(data, cameraPos, view, projection);
        int vertexCount = data == null || view == null || projection == null || cameraPos == null
                ? 18
                : projectedVertexCount;
        if (vertexBuffer == null || vertexCount <= 0) {
            vertexBuffer = getHorizonBuffer(skyColor, fogColor, groundColor, horizonY);
            vertexCount = 18;
        }
        try (RenderPass pass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(
                        () -> "Monvhua portal horizon",
                        targetFramebuffer.getColorAttachmentView(),
                        OptionalInt.empty(),
                        targetFramebuffer.getDepthAttachmentView(),
                        OptionalDouble.empty()
        )) {
            pass.setPipeline(PortalRenderPipelines.horizon());
            pass.setVertexBuffer(0, vertexBuffer);
            pass.draw(0, vertexCount);
        }
        logRendered(data, skyColor, fogColor, groundColor);
    }

    private static void logMissingData() {
        long now = System.currentTimeMillis();
        if (now - lastLogTimeMs < 3000L) {
            return;
        }
        lastLogTimeMs = now;
        MonvhuaMod.LOGGER.info("[Monvhua] Portal horizon skipped: no cached horizon data");
    }

    private static void logRendered(PortalHorizonCache.HorizonData data, int skyColor, int fogColor, int groundColor) {
        long now = System.currentTimeMillis();
        if (now - lastLogTimeMs < 3000L) {
            return;
        }
        lastLogTimeMs = now;
        MonvhuaMod.LOGGER.info(
                "[Monvhua] Portal horizon rendered: gen={}, samples={}, sky=#{}, fog=#{}, ground=#{}",
                data == null ? -1L : data.generation(),
                data == null ? 0 : data.sampleCount(),
                Integer.toHexString(skyColor),
                Integer.toHexString(fogColor),
                Integer.toHexString(groundColor)
        );
    }

    private static GpuBuffer getHorizonBuffer(int skyColor, int fogColor, int groundColor, float horizonY) {
        int quantizedHorizon = Math.round(horizonY * 4096.0F);
        int key = skyColor;
        key = 31 * key + fogColor;
        key = 31 * key + groundColor;
        key = 31 * key + quantizedHorizon;
        if (horizonBuffer != null && horizonKey == key) {
            return horizonBuffer;
        }
        if (horizonBuffer != null) {
            horizonBuffer.close();
            horizonBuffer = null;
        }

        float horizon = quantizedHorizon / 4096.0F;
        float blendTop = MathHelper.clamp(horizon - 0.08F, 0.0F, 1.0F);
        float blendBottom = MathHelper.clamp(horizon + 0.10F, 0.0F, 1.0F);

        ByteBuffer vertices = ByteBuffer.allocateDirect(18 * 16).order(ByteOrder.nativeOrder());
        putQuad(vertices, 0.0F, blendTop, 1.0F, 1.0F, skyColor, skyColor);
        putQuad(vertices, 0.0F, blendBottom, 1.0F, blendTop, fogColor, skyColor);
        putQuad(vertices, 0.0F, 0.0F, 1.0F, blendBottom, groundColor, fogColor);
        vertices.flip();

        horizonKey = key;
        horizonBuffer = RenderSystem.getDevice().createBuffer(() -> "Monvhua portal horizon", 40, vertices);
        return horizonBuffer;
    }

    private static GpuBuffer projectedTerrainBuffer;
    private static int projectedTerrainKey;
    private static int projectedVertexCount;

    private static GpuBuffer getProjectedTerrainBuffer(PortalHorizonCache.HorizonData data, Vec3d cameraPos,
                                                       Matrix4f view, Matrix4f projection) {
        int side = data.side();
        if (side < 2 || data.sampleCount() < side * side) {
            return null;
        }
        int key = Long.hashCode(data.generation());
        key = 31 * key + MathHelper.floor(cameraPos.x / 2.0D);
        key = 31 * key + MathHelper.floor(cameraPos.y / 2.0D);
        key = 31 * key + MathHelper.floor(cameraPos.z / 2.0D);
        if (projectedTerrainBuffer != null && projectedTerrainKey == key) {
            return projectedTerrainBuffer;
        }
        if (projectedTerrainBuffer != null) {
            projectedTerrainBuffer.close();
            projectedTerrainBuffer = null;
        }

        int maxQuads = (side - 1) * (side - 1);
        ByteBuffer vertices = ByteBuffer.allocateDirect(maxQuads * 6 * 16).order(ByteOrder.nativeOrder());
        Matrix4f transform = new Matrix4f(projection).mul(view);
        projectedVertexCount = 0;
        float margin = PortalViewConfig.PORTAL_HORIZON_SCREEN_MARGIN;
        int centerX = data.center().getX();
        int centerZ = data.center().getZ();
        int originOffset = -data.gridRadius() * data.stepBlocks();
        for (int z = 0; z < side - 1; z++) {
            for (int x = 0; x < side - 1; x++) {
                ProjectedVertex a = projectSample(data, transform, cameraPos, centerX, centerZ, originOffset, x, z);
                ProjectedVertex b = projectSample(data, transform, cameraPos, centerX, centerZ, originOffset, x + 1, z);
                ProjectedVertex c = projectSample(data, transform, cameraPos, centerX, centerZ, originOffset, x + 1, z + 1);
                ProjectedVertex d = projectSample(data, transform, cameraPos, centerX, centerZ, originOffset, x, z + 1);
                if (!isVisible(a, b, c, d, margin)) {
                    continue;
                }
                putProjected(vertices, a);
                putProjected(vertices, b);
                putProjected(vertices, c);
                putProjected(vertices, a);
                putProjected(vertices, c);
                putProjected(vertices, d);
                projectedVertexCount += 6;
            }
        }
        if (projectedVertexCount <= 0) {
            return null;
        }
        vertices.flip();
        projectedTerrainKey = key;
        projectedTerrainBuffer = RenderSystem.getDevice().createBuffer(() -> "Monvhua portal projected horizon", 40, vertices);
        return projectedTerrainBuffer;
    }

    private static ProjectedVertex projectSample(PortalHorizonCache.HorizonData data, Matrix4f transform, Vec3d cameraPos,
                                                 int centerX, int centerZ, int originOffset, int gridX, int gridZ) {
        double worldX = centerX + originOffset + gridX * data.stepBlocks();
        double worldY = data.heightAt(gridX, gridZ);
        double worldZ = centerZ + originOffset + gridZ * data.stepBlocks();
        double dx = worldX - cameraPos.x;
        double dy = worldY - cameraPos.y;
        double dz = worldZ - cameraPos.z;
        Vector4f clip = transform.transform(new Vector4f((float) dx, (float) dy, (float) dz, 1.0F));
        if (clip.w() <= PortalViewConfig.PORTAL_HORIZON_NEAR_CLIP) {
            return new ProjectedVertex(0.0F, 0.0F, data.colorAt(gridX, gridZ), false);
        }
        float invW = 1.0F / clip.w();
        float x = clip.x() * invW * 0.5F + 0.5F;
        float y = clip.y() * invW * 0.5F + 0.5F;
        return new ProjectedVertex(x, y, data.colorAt(gridX, gridZ), true);
    }

    private static boolean isVisible(ProjectedVertex a, ProjectedVertex b, ProjectedVertex c,
                                     ProjectedVertex d, float margin) {
        if (!a.visible || !b.visible || !c.visible || !d.visible) {
            return false;
        }
        float minX = Math.min(Math.min(a.x, b.x), Math.min(c.x, d.x));
        float maxX = Math.max(Math.max(a.x, b.x), Math.max(c.x, d.x));
        float minY = Math.min(Math.min(a.y, b.y), Math.min(c.y, d.y));
        float maxY = Math.max(Math.max(a.y, b.y), Math.max(c.y, d.y));
        return maxX >= -margin && minX <= 1.0F + margin && maxY >= -margin && minY <= 1.0F + margin;
    }

    private static void putProjected(ByteBuffer buffer, ProjectedVertex vertex) {
        putVertex(
                buffer,
                MathHelper.clamp(vertex.x, -PortalViewConfig.PORTAL_HORIZON_SCREEN_MARGIN, 1.0F + PortalViewConfig.PORTAL_HORIZON_SCREEN_MARGIN),
                MathHelper.clamp(vertex.y, -PortalViewConfig.PORTAL_HORIZON_SCREEN_MARGIN, 1.0F + PortalViewConfig.PORTAL_HORIZON_SCREEN_MARGIN),
                vertex.color
        );
    }

    private record ProjectedVertex(float x, float y, int color, boolean visible) {
    }

    private static void putQuad(ByteBuffer buffer, float left, float bottom, float right, float top,
                                int bottomColor, int topColor) {
        putVertex(buffer, left, bottom, bottomColor);
        putVertex(buffer, right, bottom, bottomColor);
        putVertex(buffer, right, top, topColor);
        putVertex(buffer, left, bottom, bottomColor);
        putVertex(buffer, right, top, topColor);
        putVertex(buffer, left, top, topColor);
    }

    private static void putVertex(ByteBuffer buffer, float x, float y, int color) {
        buffer.putFloat(x);
        buffer.putFloat(y);
        buffer.putFloat(0.0F);
        buffer.put((byte) ((color >> 16) & 0xFF));
        buffer.put((byte) ((color >> 8) & 0xFF));
        buffer.put((byte) (color & 0xFF));
        buffer.put((byte) ((color >> 24) & 0xFF));
    }

    public static void cleanup() {
        if (horizonBuffer != null) {
            horizonBuffer.close();
            horizonBuffer = null;
        }
        if (projectedTerrainBuffer != null) {
            projectedTerrainBuffer.close();
            projectedTerrainBuffer = null;
        }
    }
}
