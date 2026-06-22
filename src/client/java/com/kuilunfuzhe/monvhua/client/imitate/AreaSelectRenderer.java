package com.kuilunfuzhe.monvhua.client.imitate;

import com.kuilunfuzhe.monvhua.WitchStage;
import com.kuilunfuzhe.monvhua.item.config.ImitateConfig;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumers;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;

public class AreaSelectRenderer {

    public static void render(WorldRenderContext context) {
        if (!AreaSelectClientManager.isSelecting()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            return;
        }

        Vec3d eyePos = client.player.getEyePos();
        Vec3d lookDir = client.player.getRotationVec(1.0f);
        
        double maxDistance = 100.0;
        Vec3d farPoint = eyePos.add(lookDir.multiply(maxDistance));
        
        HitResult hit = client.player.raycast(maxDistance, 0.0f, false);
        
        Vec3d centerPoint;
        if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
            Vec3d hitPos = hit.getPos();
            centerPoint = new Vec3d(hitPos.x, hitPos.y + 1, hitPos.z);
        } else {
            centerPoint = farPoint.add(0, -eyePos.y + client.player.getY(), 0);
        }
        
        double radius = AreaSelectClientManager.getRadius();
        
        MatrixStack matrixStack = context.matrixStack();
        VertexConsumer vertexConsumer = context.consumers().getBuffer(net.minecraft.client.render.RenderLayer.getLines());
        Matrix4f matrix4f = matrixStack.peek().getPositionMatrix();
        
        renderCircle(matrix4f, vertexConsumer, centerPoint, radius);
        renderBeam(matrix4f, vertexConsumer, centerPoint);
    }

    private static void renderCircle(Matrix4f matrix4f, VertexConsumer vertexConsumer, Vec3d center, double radius) {
        int segments = 32;
        float r = 1.0f;
        float g = 1.0f;
        float b = 1.0f;
        float a = 1.0f;

        for (int i = 0; i < segments; i++) {
            double angle1 = 2 * Math.PI * i / segments;
            double angle2 = 2 * Math.PI * (i + 1) / segments;
            
            double x1 = center.x + radius * Math.cos(angle1);
            double z1 = center.z + radius * Math.sin(angle1);
            double x2 = center.x + radius * Math.cos(angle2);
            double z2 = center.z + radius * Math.sin(angle2);
            
            vertexConsumer.vertex(matrix4f, (float)x1, (float)center.y, (float)z1).color(r, g, b, a).normal(0, 1, 0);
            vertexConsumer.vertex(matrix4f, (float)x2, (float)center.y, (float)z2).color(r, g, b, a).normal(0, 1, 0);
        }
    }

    private static void renderBeam(Matrix4f matrix4f, VertexConsumer vertexConsumer, Vec3d center) {
        float r = 1.0f;
        float g = 1.0f;
        float b = 1.0f;
        float a = 1.0f;
        
        double beamHeight = 3.0;
        
        vertexConsumer.vertex(matrix4f, (float)center.x, (float)center.y, (float)center.z).color(r, g, b, a).normal(0, 1, 0);
        vertexConsumer.vertex(matrix4f, (float)center.x, (float)(center.y + beamHeight), (float)center.z).color(r, g, b, a).normal(0, 1, 0);
    }
}