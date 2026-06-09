package com.kuilunfuzhe.monvhua.features.paint;

import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public class PaintBucketBlockEntityRenderer implements BlockEntityRenderer<PaintBucketBlockEntity> {
    @Override
    public void render(PaintBucketBlockEntity entity, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay, Vec3d cameraPos) {
        if (!entity.isFilled()) {
            return;
        }
        VertexConsumer vertices = vertexConsumers.getBuffer(PaintRenderLayers.paintOverlay());
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        int color = entity.getColor();
        int r = (color >>> 16) & 0xFF;
        int g = (color >>> 8) & 0xFF;
        int b = color & 0xFF;
        int a = 255;

        float min = 0.25F;
        float max = 0.75F;
        float y = 0.63F;
        vertex(vertices, matrix, min, y, min, r, g, b, a);
        vertex(vertices, matrix, max, y, min, r, g, b, a);
        vertex(vertices, matrix, max, y, max, r, g, b, a);
        vertex(vertices, matrix, min, y, max, r, g, b, a);
    }

    private static void vertex(VertexConsumer vertices, Matrix4f matrix, float x, float y, float z, int r, int g, int b, int a) {
        vertices.vertex(matrix, x, y, z)
                .color(r, g, b, a)
                .texture(0.0F, 0.0F)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(0.0F, 1.0F, 0.0F);
    }
}
