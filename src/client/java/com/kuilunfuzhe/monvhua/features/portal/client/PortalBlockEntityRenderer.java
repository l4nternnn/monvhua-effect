package com.kuilunfuzhe.monvhua.features.portal.client;

import com.kuilunfuzhe.monvhua.features.portal.PortalBlock;
import com.kuilunfuzhe.monvhua.features.portal.PortalBlockEntity;
import com.kuilunfuzhe.monvhua.features.portal.PortalFrame;
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
        PortalFrame entityFrame = entity.getFrame();
        double cameraSide = cameraPos.subtract(entityFrame.center())
                .dotProduct(entityFrame.normal());
        if (cameraSide <= 0.0D) {
            return;
        }
        PortalLinkData link = entity.getLinkData();
        PortalFramebufferRenderer.offerVisiblePortal(entity, cameraPos);
        if (entity.isActive() && link != null) {
            return;
        }

        int color = colorForState(entity, link);
        RenderLayer layer = RenderLayer.getEntityTranslucent(FALLBACK_TEXTURE);
        VertexConsumer vertices = vertexConsumers.getBuffer(layer);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        boolean baseReverse = facing == Direction.SOUTH || facing == Direction.EAST || facing == Direction.DOWN;
        renderFace(matrix, vertices, facing, color, light, overlay,
                entity.getPortalWidth(), entity.getPortalHeight(), baseReverse, false);
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
        float w = Math.max(1, width);
        float h = Math.max(1, height);
        float horizontalInset = (float) PortalViewConfig.PORTAL_SURFACE_HORIZONTAL_INSET;
        float verticalInset = (float) PortalViewConfig.PORTAL_SURFACE_VERTICAL_INSET;
        double halfWidth = Math.max(0.01D, w * 0.5D - horizontalInset);
        double halfHeight = Math.max(0.01D, h * 0.5D - verticalInset);
        PortalFrame frame = PortalFrame.local(facing, width, height);
        Vec3d renderWidthAxis = frame.widthAxis();
        Vec3d renderHeightAxis = frame.heightAxis();
        Vec3d center = frame.center();
        Vec3d bottomLeft = center
                .subtract(renderWidthAxis.multiply(halfWidth))
                .subtract(renderHeightAxis.multiply(halfHeight));
        Vec3d bottomRight = center
                .add(renderWidthAxis.multiply(halfWidth))
                .subtract(renderHeightAxis.multiply(halfHeight));
        Vec3d topRight = center
                .add(renderWidthAxis.multiply(halfWidth))
                .add(renderHeightAxis.multiply(halfHeight));
        Vec3d topLeft = center
                .subtract(renderWidthAxis.multiply(halfWidth))
                .add(renderHeightAxis.multiply(halfHeight));
        float nx = (float) frame.normal().x;
        float ny = (float) frame.normal().y;
        float nz = (float) frame.normal().z;
        float u0 = reverseU ? 1.0F : 0.0F;
        float u1 = reverseU ? 0.0F : 1.0F;

        vertex(matrix, vertices, bottomLeft, u0, 0.0F, r, g, b, a,
                light, overlay, nx, ny, nz, portalSurface);
        vertex(matrix, vertices, bottomRight, u1, 0.0F, r, g, b, a,
                light, overlay, nx, ny, nz, portalSurface);
        vertex(matrix, vertices, topRight, u1, 1.0F, r, g, b, a,
                light, overlay, nx, ny, nz, portalSurface);
        vertex(matrix, vertices, topLeft, u0, 1.0F, r, g, b, a,
                light, overlay, nx, ny, nz, portalSurface);
    }

    private static void vertex(Matrix4f matrix, VertexConsumer vertices,
                               Vec3d pos,
                               float u, float v,
                               float r, float g, float b, float a,
                               int light, int overlay, float nx, float ny, float nz,
                               boolean portalSurface) {
        if (portalSurface) {
            vertices.vertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z)
                    .texture(u, v)
                    .color(r, g, b, a);
            return;
        }
        vertices.vertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z)
                .color(r, g, b, a)
                .texture(u, v)
                .overlay(overlay)
                .light(light)
                .normal(nx, ny, nz);
    }
}
