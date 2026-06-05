package com.kuilunfuzhe.monvhua.renderer.block_hole_render;

import com.kuilunfuzhe.monvhua.features.block_hole.BlockHoleBlockEntity;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public class BlockHoleBlockEntityRenderer implements BlockEntityRenderer<BlockHoleBlockEntity> {
    private static final Vec3d LOCAL_CENTER = new Vec3d(0.5D, 1.5D, 0.5D);
    private static final double HALF_SIZE = 0.88D;

    @Override
    public void render(BlockHoleBlockEntity entity, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay, Vec3d cameraPos) {
        VertexConsumer vertices = vertexConsumers.getBuffer(BlockHoleRenderLayers.blockHole());
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        Vec3d centerWorld = Vec3d.of(entity.getPos()).add(LOCAL_CENTER);
        Vec3d toCamera = cameraPos.subtract(centerWorld);
        if (toCamera.lengthSquared() < 0.0001D) {
            toCamera = new Vec3d(0.0D, 0.0D, 1.0D);
        }

        Vec3d normal = toCamera.normalize();
        Vec3d upReference = Math.abs(normal.y) > 0.96D ? new Vec3d(0.0D, 0.0D, 1.0D) : new Vec3d(0.0D, 1.0D, 0.0D);
        Vec3d right = upReference.crossProduct(normal).normalize().multiply(HALF_SIZE);
        Vec3d up = normal.crossProduct(right.normalize()).normalize().multiply(HALF_SIZE);

        renderQuad(matrix, vertices, LOCAL_CENTER, right, up);
    }

    @Override
    public boolean rendersOutsideBoundingBox() {
        return true;
    }

    @Override
    public int getRenderDistance() {
        return 192;
    }

    private static void renderQuad(Matrix4f matrix, VertexConsumer vertices, Vec3d center, Vec3d right, Vec3d up) {
        Vec3d bottomLeft = center.subtract(right).subtract(up);
        Vec3d bottomRight = center.add(right).subtract(up);
        Vec3d topRight = center.add(right).add(up);
        Vec3d topLeft = center.subtract(right).add(up);

        vertex(matrix, vertices, bottomLeft, 0.0F, 1.0F);
        vertex(matrix, vertices, bottomRight, 1.0F, 1.0F);
        vertex(matrix, vertices, topRight, 1.0F, 0.0F);
        vertex(matrix, vertices, topLeft, 0.0F, 0.0F);
    }

    private static void vertex(Matrix4f matrix, VertexConsumer vertices, Vec3d pos, float u, float v) {
        vertices.vertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z).texture(u, v);
    }
}
