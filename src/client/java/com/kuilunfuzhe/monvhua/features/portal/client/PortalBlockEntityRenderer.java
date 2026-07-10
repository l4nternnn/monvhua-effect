package com.kuilunfuzhe.monvhua.features.portal.client;

import com.kuilunfuzhe.monvhua.features.portal.PortalBlock;
import com.kuilunfuzhe.monvhua.features.portal.PortalBlockEntity;
import com.kuilunfuzhe.monvhua.features.portal.PortalLinkData;
import com.kuilunfuzhe.monvhua.features.portal.PortalViewConfig;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public class PortalBlockEntityRenderer implements BlockEntityRenderer<PortalBlockEntity> {
    private static final Identifier FALLBACK_TEXTURE = Identifier.of("monvhua", "textures/block/1testblock.png");
    public PortalBlockEntityRenderer(BlockEntityRendererFactory.Context context) {
    }

    @Override
    public void render(PortalBlockEntity entity, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay, Vec3d cameraPos) {
        if (!entity.isController() || PortalFramebufferRenderer.isRenderingPortalView()) {
            return;
        }

        Direction facing = entity.getCachedState().contains(PortalBlock.FACING)
                ? entity.getCachedState().get(PortalBlock.FACING)
                : Direction.NORTH;
        double cameraSide = cameraPos.subtract(entity.getPortalCenter())
                .dotProduct(new Vec3d(facing.getOffsetX(), 0.0D, facing.getOffsetZ()));
        if (cameraSide <= 0.0D) {
            return;
        }
        PortalLinkData link = entity.getLinkData();
        PortalFramebufferRenderer.offerVisiblePortal(entity, cameraPos);

        Identifier viewTexture = entity.isActive() ? PortalFramebufferRenderer.getTextureIdFor(entity) : null;
        int color = viewTexture == null ? colorForState(entity, link) : 0xFFFFFFFF;
        RenderLayer layer = viewTexture == null
                ? RenderLayer.getEntityTranslucent(FALLBACK_TEXTURE)
                : PortalRenderLayers.surface(viewTexture);
        VertexConsumer vertices = vertexConsumers.getBuffer(layer);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        boolean baseReverse = facing == Direction.SOUTH || facing == Direction.EAST;
        renderFace(matrix, vertices, facing, color, light, overlay,
                entity.getPortalWidth(), entity.getPortalHeight(), baseReverse, viewTexture != null);
    }

    @Override
    public boolean rendersOutsideBoundingBox() {
        return true;
    }

    @Override
    public int getRenderDistance() {
        return 160;
    }

    private static int colorForState(PortalBlockEntity entity, PortalLinkData link) {
        if (!entity.isController()) {
            return 0x33404050;
        }
        if (link == null) {
            return 0x66404050;
        }
        if (!entity.isActive()) {
            return 0xAA508088;
        }
        return colorFor(link, entity.getPos().asLong());
    }

    private static int colorFor(PortalLinkData link, long seed) {
        long value = link.targetPos().asLong() ^ seed;
        int r = 56 + (int) (value & 0x3F);
        int g = 148 + (int) ((value >> 8) & 0x3F);
        int b = 190 + (int) ((value >> 16) & 0x3F);
        return 0xCC000000 | (r << 16) | (g << 8) | b;
    }

    private static void renderFace(Matrix4f matrix, VertexConsumer vertices, Direction facing, int argb,
                                   int light, int overlay, int width, int height,
                                   boolean reverseU, boolean portalSurface) {
        float a = ((argb >>> 24) & 0xFF) / 255.0F;
        float r = ((argb >>> 16) & 0xFF) / 255.0F;
        float g = ((argb >>> 8) & 0xFF) / 255.0F;
        float b = (argb & 0xFF) / 255.0F;
        float nx = facing.getOffsetX();
        float nz = facing.getOffsetZ();

        float w = Math.max(1, width);
        float h = Math.max(1, height);
        float horizontalInset = (float) PortalViewConfig.PORTAL_SURFACE_HORIZONTAL_INSET;
        float verticalInset = (float) PortalViewConfig.PORTAL_SURFACE_VERTICAL_INSET;
        float min = horizontalInset;
        float max = Math.max(1.0F - horizontalInset, w - horizontalInset);
        float bottom = verticalInset;
        float top = Math.max(1.0F - verticalInset, h - verticalInset);
        float u0 = reverseU ? 1.0F : 0.0F;
        float u1 = reverseU ? 0.0F : 1.0F;

        if (facing.getAxis() == Direction.Axis.X) {
            float x = 0.5F;
            vertex(matrix, vertices, x, bottom, min, u0, 0.0F, r, g, b, a,
                    light, overlay, nx, nz, portalSurface);
            vertex(matrix, vertices, x, bottom, max, u1, 0.0F, r, g, b, a,
                    light, overlay, nx, nz, portalSurface);
            vertex(matrix, vertices, x, top, max, u1, 1.0F, r, g, b, a,
                    light, overlay, nx, nz, portalSurface);
            vertex(matrix, vertices, x, top, min, u0, 1.0F, r, g, b, a,
                    light, overlay, nx, nz, portalSurface);
        } else {
            float z = 0.5F;
            vertex(matrix, vertices, max, bottom, z, u0, 0.0F, r, g, b, a,
                    light, overlay, nx, nz, portalSurface);
            vertex(matrix, vertices, min, bottom, z, u1, 0.0F, r, g, b, a,
                    light, overlay, nx, nz, portalSurface);
            vertex(matrix, vertices, min, top, z, u1, 1.0F, r, g, b, a,
                    light, overlay, nx, nz, portalSurface);
            vertex(matrix, vertices, max, top, z, u0, 1.0F, r, g, b, a,
                    light, overlay, nx, nz, portalSurface);
        }
    }

    private static void vertex(Matrix4f matrix, VertexConsumer vertices,
                               float x, float y, float z,
                               float u, float v,
                               float r, float g, float b, float a,
                               int light, int overlay, float nx, float nz,
                               boolean portalSurface) {
        if (portalSurface) {
            vertices.vertex(matrix, x, y, z)
                    .texture(u, v)
                    .color(r, g, b, a);
            return;
        }
        vertices.vertex(matrix, x, y, z)
                .color(r, g, b, a)
                .texture(u, v)
                .overlay(overlay)
                .light(light)
                .normal(nx, 0.0F, nz);
    }
}
