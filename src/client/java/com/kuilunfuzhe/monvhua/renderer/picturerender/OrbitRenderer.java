package com.kuilunfuzhe.monvhua.renderer.picturerender;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public class OrbitRenderer {
    private record OrbitImage(Identifier texture, double radius, double speed, double yOffset, float selfRotateSpeed, double startAngle) {}

    private static final List<OrbitImage> ORBIT_IMAGES = new ArrayList<>();
    static {
        ORBIT_IMAGES.add(new OrbitImage(Identifier.of("monvhua", "textures/gui/7.png"), 0.5, 5.0, -0.7, 10.0f,0.0));
        ORBIT_IMAGES.add(new OrbitImage(Identifier.of("monvhua", "textures/gui/8.png"), 0.5, 5.0, -0.7 ,10.0f,45.0));
        ORBIT_IMAGES.add(new OrbitImage(Identifier.of("monvhua", "textures/gui/7.png"), 0.5, 5.0, -0.7, 10.0f,90.0));
        ORBIT_IMAGES.add(new OrbitImage(Identifier.of("monvhua", "textures/gui/8.png"), 0.5, 5.0, -0.7, 10.0f,135.0));
        ORBIT_IMAGES.add(new OrbitImage(Identifier.of("monvhua", "textures/gui/7.png"), 0.5, 5.0, -0.7, 10.0f,180.0));
        ORBIT_IMAGES.add(new OrbitImage(Identifier.of("monvhua", "textures/gui/8.png"), 0.5, 5.0, -0.7 ,10.0f,225.0));
        ORBIT_IMAGES.add(new OrbitImage(Identifier.of("monvhua", "textures/gui/7.png"), 0.5, 5.0, -0.7, 10.0f,270.0));
        ORBIT_IMAGES.add(new OrbitImage(Identifier.of("monvhua", "textures/gui/8.png"), 0.5, 5.0, -0.7, 10.0f,315.0));
    }

    public static void render(MatrixStack matrices, VertexConsumerProvider consumers, PlayerEntity player, int currentPlayerStage) {
        if (!BackTextureRenderer.imagesEnabled) return;
        String playerName = player.getName().getString();
        boolean isSpecial = BackTextureRenderer.SPECIAL_NAMES.contains(playerName);
        List<OrbitImage> imagesToRender = new ArrayList<>();
        if (isSpecial && currentPlayerStage == 7) {
            imagesToRender.addAll(ORBIT_IMAGES);
        } else if (currentPlayerStage == 7 && !ORBIT_IMAGES.isEmpty()) {
            imagesToRender.add(ORBIT_IMAGES.get(0));
            imagesToRender.add(ORBIT_IMAGES.get(2));
            imagesToRender.add(ORBIT_IMAGES.get(4));
            imagesToRender.add(ORBIT_IMAGES.get(6));
        }
        if (imagesToRender.isEmpty()) return;

        if (player == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;
        Camera camera = client.gameRenderer.getCamera();
        Vec3d targetPos = player.getPos().add(0, player.getHeight() / 2, 0);
        Vec3d center = player.getPos().add(0, player.getHeight() * 0.5, 0);
        long gameTime = player.getWorld().getTime();
        double angleBase = (gameTime / 20.0) * 360;

        for (OrbitImage img : imagesToRender) {
            double angle = angleBase * (img.speed / 360.0) + img.startAngle;
            double rad = Math.toRadians(angle);
            double x = img.radius * Math.cos(rad);
            double z = img.radius * Math.sin(rad);
            Vec3d worldPos = center.add(x, img.yOffset, z);

            int light = WorldRenderer.getLightmapCoordinates(client.world, BlockPos.ofFloored(worldPos));
            int overlay = OverlayTexture.DEFAULT_UV;
            float size = 0.2f;
            float half = size / 2;

            matrices.push();
            matrices.translate(worldPos.x - targetPos.x, worldPos.y - targetPos.y, worldPos.z - targetPos.z);
            float yaw = (float) Math.toDegrees(Math.atan2(targetPos.z - worldPos.z, targetPos.x - worldPos.x)) + 90;
            float pitch = (float) Math.toDegrees(Math.atan2(targetPos.y - worldPos.y,
                    Math.hypot(targetPos.x - worldPos.x, targetPos.z - worldPos.z)));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
            float selfAngle = (float) ((gameTime * img.selfRotateSpeed / 20.0) % 360);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(selfAngle));

            RenderLayer layer = RenderLayer.getEntityTranslucent(img.texture);
            VertexConsumer vertex = consumers.getBuffer(layer);
            Matrix4f posMat = matrices.peek().getPositionMatrix();

            vertex.vertex(posMat, -half, -half, 0).color(255,255,255,128).texture(0,0).overlay(overlay).light(light).normal(0,0,1);
            vertex.vertex(posMat, -half, half, 0).color(255,255,255,128).texture(0,1).overlay(overlay).light(light).normal(0,0,1);
            vertex.vertex(posMat, half, half, 0).color(255,255,255,128).texture(1,1).overlay(overlay).light(light).normal(0,0,1);
            vertex.vertex(posMat, half, -half, 0).color(255,255,255,128).texture(1,0).overlay(overlay).light(light).normal(0,0,1);
            matrices.pop();
        }
    }
}
