package com.kuilunfuzhe.monvhua.features.cosmic_box;

import com.kuilunfuzhe.monvhua.item.cosmic_box.CosmicBoxItems;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.UUID;

public class CosmicBoxBlockEntityRenderer implements BlockEntityRenderer<CosmicBoxBlockEntity> {
    private static final float MAIN_BEAM_RADIUS = 0.18F;
    private static final float TARGET_BEAM_RADIUS = MAIN_BEAM_RADIUS * 0.5F;

    @Override
    public void render(CosmicBoxBlockEntity entity, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay, Vec3d cameraPos) {
        VertexConsumer vertices = vertexConsumers.getBuffer(CosmicBoxRenderLayers.cosmicBox());
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        renderCube(matrix, vertices);
        renderBeams(entity, matrix, vertices, tickDelta);
    }

    @Override
    public boolean rendersOutsideBoundingBox() {
        return true;
    }

    @Override
    public int getRenderDistance() {
        return 1024;
    }

    private static void renderBeams(CosmicBoxBlockEntity box, Matrix4f matrix, VertexConsumer vertices, float tickDelta) {
        if (box.getCachedState().getBlock() != CosmicBoxItems.COSMIC_BOX_BLOCK) {
            return;
        }
        if (!box.isBeamActive() || box.getWorld() == null || box.getTargets().isEmpty()) {
            return;
        }
        if (!canCurrentPlayerSeeBeam()) {
            return;
        }

        BlockPos blockPos = box.getPos();
        double topY = box.getWorld().getBottomY() + box.getWorld().getHeight();
        double localTopY = Math.max(1.0D, topY - blockPos.getY());
        Vec3d base = new Vec3d(0.5D, 1.0D, 0.5D);
        Vec3d top = new Vec3d(0.5D, localTopY, 0.5D);
        renderBeam(matrix, vertices, base, top, MAIN_BEAM_RADIUS);

        for (CosmicBoxBlockEntity.TargetRef targetRef : box.getTargets()) {
            Entity target = findTarget(targetRef.uuid());
            if (target == null) {
                continue;
            }

            Vec3d targetPos = target.getPos().add(0.0D, target.getHeight() * 0.5D + 5.0D, 0.0D)
                    .subtract(blockPos.getX(), blockPos.getY(), blockPos.getZ());
            renderBeam(matrix, vertices, top, targetPos, TARGET_BEAM_RADIUS);
        }
    }

    private static boolean canCurrentPlayerSeeBeam() {
        return CosmicBoxClientState.canSeeBeam();
    }

    private static Entity findTarget(UUID uuid) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return null;
        }
        return client.world.getEntity(uuid);
    }

    private static void renderBeam(Matrix4f matrix, VertexConsumer vertices, Vec3d start, Vec3d end, float radius) {
        Vec3d axis = end.subtract(start);
        if (axis.lengthSquared() < 0.0001D) {
            return;
        }

        Vec3d direction = axis.normalize();
        Vec3d reference = Math.abs(direction.y) > 0.92D ? new Vec3d(1.0D, 0.0D, 0.0D) : new Vec3d(0.0D, 1.0D, 0.0D);
        Vec3d side = direction.crossProduct(reference).normalize().multiply(radius);
        Vec3d other = direction.crossProduct(side).normalize().multiply(radius);
        Vec3d[] offsets = new Vec3d[] {
                side,
                other,
                side.negate(),
                other.negate()
        };

        for (int i = 0; i < offsets.length; i++) {
            Vec3d a = offsets[i];
            Vec3d b = offsets[(i + 1) % offsets.length];
            vertex(matrix, vertices, start.add(a));
            vertex(matrix, vertices, start.add(b));
            vertex(matrix, vertices, end.add(b));
            vertex(matrix, vertices, end.add(a));
        }
    }

    private static void renderCube(Matrix4f matrix, VertexConsumer vertices) {
        quad(matrix, vertices, 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F, 0.0F, 1.0F, 1.0F);
        quad(matrix, vertices, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 1.0F, 1.0F, 0.0F);
        quad(matrix, vertices, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 0.0F, 0.0F, 1.0F, 0.0F);
        quad(matrix, vertices, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 1.0F, 0.0F, 1.0F, 0.0F, 0.0F, 1.0F);
        quad(matrix, vertices, 1.0F, 0.0F, 1.0F, 1.0F, 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, 1.0F, 1.0F, 1.0F);
        quad(matrix, vertices, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 1.0F, 1.0F, 0.0F, 1.0F, 0.0F);
    }

    private static void quad(Matrix4f matrix, VertexConsumer vertices,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float x4, float y4, float z4) {
        vertices.vertex(matrix, x1, y1, z1);
        vertices.vertex(matrix, x2, y2, z2);
        vertices.vertex(matrix, x3, y3, z3);
        vertices.vertex(matrix, x4, y4, z4);
    }

    private static void vertex(Matrix4f matrix, VertexConsumer vertices, Vec3d pos) {
        vertices.vertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z);
    }
}
