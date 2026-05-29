package com.kuilunfuzhe.monvhua.renderer.picturerender;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AnchorButtonRenderer {
    public static class AnchorInfo {
        public Vec3d pos;
        public long lastSeenTime;
    }

    public static final Map<UUID, AnchorInfo> anchors = new ConcurrentHashMap<>();
    private static final double SPHERE_RADIUS = 0.2;

    public static void render(MatrixStack matrices, VertexConsumerProvider consumers) {
        if (!BackTextureRenderer.imagesEnabled) return;
        if (anchors.isEmpty()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;
        Camera camera = client.gameRenderer.getCamera();
        Vec3d targetPos = camera.getPos();
        Identifier buttonTexture = Identifier.of("monvhua", "textures/gui/yj5.png");
        float size = 1.0f;
        float half = size / 2;
        int overlay = OverlayTexture.DEFAULT_UV;

        Vec3d playerChest = client.player.getEyePos();
        double offsetX = 0.0, offsetY = -0.4, offsetZ = 0.0;
        Iterator<Map.Entry<UUID, AnchorInfo>> it = anchors.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, AnchorInfo> entry = it.next();
            if (client.world.getEntity(entry.getKey()) == null) {
                it.remove();
                continue;
            }
            AnchorInfo info = entry.getValue();
            Vec3d armorStandPos = info.pos;
            Vec3d center = armorStandPos.add(offsetX, offsetY, offsetZ);
            Vec3d dir = playerChest.subtract(center).normalize();
            Vec3d worldPos = center.add(dir.multiply(SPHERE_RADIUS));
            int light = 0xF000F0;

            matrices.push();
            matrices.translate(worldPos.x - targetPos.x, worldPos.y - targetPos.y, worldPos.z - targetPos.z);
            float yaw = (float) Math.toDegrees(Math.atan2(targetPos.z - worldPos.z, targetPos.x - worldPos.x)) + 90;
            float pitch = (float) Math.toDegrees(Math.atan2(targetPos.y - worldPos.y,
                    Math.hypot(targetPos.x - worldPos.x, targetPos.z - worldPos.z)));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));

            RenderLayer layer = RenderLayer.getEntityTranslucent(buttonTexture);
            VertexConsumer vertex = consumers.getBuffer(layer);
            Matrix4f posMat = matrices.peek().getPositionMatrix();

            vertex.vertex(posMat, half, half, 0).color(255,255,255,255).texture(0,0).overlay(overlay).light(light).normal(0,0,1);
            vertex.vertex(posMat, half, -half, 0).color(255,255,255,255).texture(0,1).overlay(overlay).light(light).normal(0,0,1);
            vertex.vertex(posMat, -half, -half, 0).color(255,255,255,255).texture(1,1).overlay(overlay).light(light).normal(0,0,1);
            vertex.vertex(posMat, -half, half, 0).color(255,255,255,255).texture(1,0).overlay(overlay).light(light).normal(0,0,1);
            matrices.pop();
        }
    }
}
