package com.kuilunfuzhe.monvhua.features.gravity;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public final class GravityAreaBoundaryRenderer {
    private static final int PERIOD_TICKS = 200;
    private static final int VISIBLE_TICKS = 20;
    private static final int SEGMENTS = 96;

    private GravityAreaBoundaryRenderer() {
    }

    public static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        PlayerEntity player = client.player;
        if (client.world == null || player == null || !player.isCreative()) {
            return;
        }
        if (client.world.getTime() % PERIOD_TICKS >= VISIBLE_TICKS) {
            return;
        }

        VertexConsumer vertices = context.consumers().getBuffer(RenderLayer.getLines());
        Vec3d camera = context.camera().getPos();
        MatrixStack matrices = context.matrixStack();
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        for (GravityMagic.AreaGravityView view : GravityMagic.getClientAreaGravityViews(client.world.getRegistryKey())) {
            Vec3d center = Vec3d.ofCenter(view.center()).subtract(camera);
            drawRing(vertices, matrix, center, view.radius(), RingPlane.XZ);
            drawRing(vertices, matrix, center, view.radius(), RingPlane.XY);
            drawRing(vertices, matrix, center, view.radius(), RingPlane.YZ);
        }
    }

    private static void drawRing(VertexConsumer vertices, Matrix4f matrix, Vec3d center, double radius, RingPlane plane) {
        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = Math.PI * 2.0D * i / SEGMENTS;
            double a1 = Math.PI * 2.0D * (i + 1) / SEGMENTS;
            Vec3d p0 = point(center, radius, plane, a0);
            Vec3d p1 = point(center, radius, plane, a1);
            line(vertices, matrix, p0, p1);
        }
    }

    private static Vec3d point(Vec3d center, double radius, RingPlane plane, double angle) {
        double c = Math.cos(angle) * radius;
        double s = Math.sin(angle) * radius;
        return switch (plane) {
            case XZ -> center.add(c, 0.0D, s);
            case XY -> center.add(c, s, 0.0D);
            case YZ -> center.add(0.0D, c, s);
        };
    }

    private static void line(VertexConsumer vertices, Matrix4f matrix, Vec3d from, Vec3d to) {
        vertices.vertex(matrix, (float) from.x, (float) from.y, (float) from.z).color(64, 255, 96, 220).normal(0.0F, 1.0F, 0.0F);
        vertices.vertex(matrix, (float) to.x, (float) to.y, (float) to.z).color(64, 255, 96, 220).normal(0.0F, 1.0F, 0.0F);
    }

    private enum RingPlane {
        XZ,
        XY,
        YZ
    }
}
