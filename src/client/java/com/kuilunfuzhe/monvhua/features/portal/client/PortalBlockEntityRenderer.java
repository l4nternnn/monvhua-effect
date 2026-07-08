package com.kuilunfuzhe.monvhua.features.portal.client;

import com.kuilunfuzhe.monvhua.features.portal.PortalBlock;
import com.kuilunfuzhe.monvhua.features.portal.PortalBlockEntity;
import com.kuilunfuzhe.monvhua.features.portal.PortalLinkData;
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
    private static final float HALF_WIDTH = 0.47F;
    private static final float MIN_Y = 0.05F;
    private static final float MAX_Y = 0.95F;

    public PortalBlockEntityRenderer(BlockEntityRendererFactory.Context context) {
    }

    @Override
    public void render(PortalBlockEntity entity, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay, Vec3d cameraPos) {
        if (!entity.isController()) {
            return;
        }

        Direction facing = entity.getCachedState().contains(PortalBlock.FACING)
                ? entity.getCachedState().get(PortalBlock.FACING)
                : Direction.NORTH;
        PortalLinkData link = entity.getLinkData();

        Identifier viewTexture = entity.isActive() ? PortalFramebufferRenderer.getTextureIdFor(entity.getPos()) : null;
        int color = viewTexture == null ? colorForState(entity, link) : 0xFFFFFFFF;
        RenderLayer layer = RenderLayer.getEntityTranslucent(viewTexture == null ? FALLBACK_TEXTURE : viewTexture);
        VertexConsumer vertices = vertexConsumers.getBuffer(layer);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        renderFace(matrix, vertices, facing, color, light, overlay, entity.getPortalWidth(), entity.getPortalHeight());
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

    private static void renderFace(Matrix4f matrix, VertexConsumer vertices, Direction facing, int argb, int light, int overlay, int width, int height) {
        float a = ((argb >>> 24) & 0xFF) / 255.0F;
        float r = ((argb >>> 16) & 0xFF) / 255.0F;
        float g = ((argb >>> 8) & 0xFF) / 255.0F;
        float b = (argb & 0xFF) / 255.0F;
        float c = 0.5F;
        float nx = facing.getOffsetX();
        float nz = facing.getOffsetZ();

        float w = Math.max(1, width);
        float h = Math.max(1, height);
        float min = 0.03F;
        float max = Math.max(0.97F, w - 0.03F);
        float top = Math.max(MAX_Y, h - 0.05F);

        if (facing.getAxis() == Direction.Axis.X) {
            float x = facing == Direction.EAST ? 0.97F : 0.03F;
            vertex(matrix, vertices, x, MIN_Y, min, 0.0F, 1.0F, r, g, b, a, light, overlay, nx, nz);
            vertex(matrix, vertices, x, MIN_Y, max, 1.0F, 1.0F, r, g, b, a, light, overlay, nx, nz);
            vertex(matrix, vertices, x, top, max, 1.0F, 0.0F, r, g, b, a, light, overlay, nx, nz);
            vertex(matrix, vertices, x, top, min, 0.0F, 0.0F, r, g, b, a, light, overlay, nx, nz);
        } else {
            float z = facing == Direction.SOUTH ? 0.97F : 0.03F;
            vertex(matrix, vertices, max, MIN_Y, z, 0.0F, 1.0F, r, g, b, a, light, overlay, nx, nz);
            vertex(matrix, vertices, min, MIN_Y, z, 1.0F, 1.0F, r, g, b, a, light, overlay, nx, nz);
            vertex(matrix, vertices, min, top, z, 1.0F, 0.0F, r, g, b, a, light, overlay, nx, nz);
            vertex(matrix, vertices, max, top, z, 0.0F, 0.0F, r, g, b, a, light, overlay, nx, nz);
        }
    }

    private static void vertex(Matrix4f matrix, VertexConsumer vertices,
                               float x, float y, float z,
                               float u, float v,
                               float r, float g, float b, float a,
                               int light, int overlay, float nx, float nz) {
        vertices.vertex(matrix, x, y, z)
                .color(r, g, b, a)
                .texture(u, v)
                .overlay(overlay)
                .light(light)
                .normal(nx, 0.0F, nz);
    }
}
