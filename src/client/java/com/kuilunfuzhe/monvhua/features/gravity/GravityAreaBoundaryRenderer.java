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
    private static final int LATITUDE_RINGS = 4;
    private static final int MERIDIANS = 8;

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
            Vec3d center = GravityMagic.areaTopCenter(view.center()).subtract(camera);
            drawHemisphere(vertices, matrix, center, view.radius(), view.height());
        }
    }

    private static void drawHemisphere(VertexConsumer vertices, Matrix4f matrix, Vec3d topCenter, double radius, double height) {
        drawHorizontalRing(vertices, matrix, topCenter, radius, 0.0D);

        for (int i = 1; i <= LATITUDE_RINGS; i++) {
            double t = i / (double) LATITUDE_RINGS;
            double ringRadius = radius * Math.sqrt(Math.max(0.0D, 1.0D - t * t));
            if (ringRadius > 0.05D) {
                drawHorizontalRing(vertices, matrix, topCenter, ringRadius, -height * t);
            }
        }

        for (int i = 0; i < MERIDIANS; i++) {
            double angle = Math.PI * 2.0D * i / MERIDIANS;
            drawMeridian(vertices, matrix, topCenter, radius, height, angle);
        }
    }

    private static void drawHorizontalRing(VertexConsumer vertices, Matrix4f matrix, Vec3d center, double radius, double yOffset) {
        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = Math.PI * 2.0D * i / SEGMENTS;
            double a1 = Math.PI * 2.0D * (i + 1) / SEGMENTS;
            Vec3d p0 = center.add(Math.cos(a0) * radius, yOffset, Math.sin(a0) * radius);
            Vec3d p1 = center.add(Math.cos(a1) * radius, yOffset, Math.sin(a1) * radius);
            line(vertices, matrix, p0, p1);
        }
    }

    private static void drawMeridian(VertexConsumer vertices, Matrix4f matrix, Vec3d topCenter, double radius, double height, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        int arcSegments = SEGMENTS / 4;
        for (int i = 0; i < arcSegments; i++) {
            double a0 = Math.PI * 0.5D * i / arcSegments;
            double a1 = Math.PI * 0.5D * (i + 1) / arcSegments;
            Vec3d p0 = meridianPoint(topCenter, radius, height, cos, sin, a0);
            Vec3d p1 = meridianPoint(topCenter, radius, height, cos, sin, a1);
            line(vertices, matrix, p0, p1);
        }
    }

    private static Vec3d meridianPoint(Vec3d topCenter, double radius, double height, double cos, double sin, double angle) {
        double horizontalRadius = radius * Math.cos(angle);
        double y = -height * Math.sin(angle);
        return topCenter.add(horizontalRadius * cos, y, horizontalRadius * sin);
    }

    private static void line(VertexConsumer vertices, Matrix4f matrix, Vec3d from, Vec3d to) {
        vertices.vertex(matrix, (float) from.x, (float) from.y, (float) from.z).color(64, 255, 96, 220).normal(0.0F, 1.0F, 0.0F);
        vertices.vertex(matrix, (float) to.x, (float) to.y, (float) to.z).color(64, 255, 96, 220).normal(0.0F, 1.0F, 0.0F);
    }

}
