package com.kuilunfuzhe.monvhua.renderer.picturerender;

import com.kuilunfuzhe.monvhua.features.evil_eyes.Evil_Eyes;
import com.kuilunfuzhe.monvhua.item.gazeguidance.ModItems;
import com.kuilunfuzhe.monvhua.item.secrecy.SecrecyItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class BackTextureRenderer {
    public static boolean imagesEnabled = false;
    public static final Set<String> SPECIAL_NAMES = Set.of("shushuwonie", "Remio");

    private record BackImage(Identifier texture, float xOffset, float yOffset, float zOffset, float width, float height, float selfRotateZ) {}

    private static final List<BackImage> BACK_IMAGES = new ArrayList<>();
    static {
        BACK_IMAGES.add(new BackImage(Identifier.of("monvhua", "textures/gui/6.png"), 0.0f, 1.0f, 3.0f, 3.5f, 3.5f, 1.0f));
        BACK_IMAGES.add(new BackImage(Identifier.of("monvhua", "textures/gui/3.png"), -2f, 0.8f, 3.0f, 2.0f, 2.0f, -1.5f));
        BACK_IMAGES.add(new BackImage(Identifier.of("monvhua", "textures/gui/3.png"), 2f, 0.8f, 3.0f, 2.0f, 2.0f, -1.5f));
        BACK_IMAGES.add(new BackImage(Identifier.of("monvhua", "textures/gui/11.png"), 0.0f, 3f, 4.0f, 2.0f, 2.0f, 3.0f));
    }

    public static void render(MatrixStack matrices, VertexConsumerProvider consumers, PlayerEntity player, int currentPlayerStage) {
        if (!imagesEnabled) return;
        ItemStack mainHand = player.getMainHandStack();
        boolean hasValidItem = mainHand.getItem() == Evil_Eyes.CLAIRVOYANCE_ITEM || mainHand.getItem() == ModItems.MAGIC_STICK || SecrecyItem.isHoldingSecrecy(mainHand);
        if (!hasValidItem) return;

        String playerName = player.getName().getString();
        boolean isSpecial = SPECIAL_NAMES.contains(playerName);
        List<BackImage> imagesToRender = new ArrayList<>();
        if (isSpecial && currentPlayerStage == 7) {
            imagesToRender.addAll(BACK_IMAGES);
        } else if (currentPlayerStage == 7 && !BACK_IMAGES.isEmpty()) {
            imagesToRender.add(BACK_IMAGES.get(0));
        }
        if (imagesToRender.isEmpty()) return;

        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
        Vec3d targetPos = camera.getPos();
        Vec3d forward = player.getRotationVec(1.0f);
        Vec3d backDir = forward.multiply(-1);
        Vec3d rightDir = new Vec3d(forward.z, 0, -forward.x).normalize();

        matrices.push();
        for (BackImage img : imagesToRender) {
            Vec3d worldPos = player.getPos()
                    .add(backDir.multiply(img.zOffset))
                    .add(rightDir.multiply(img.xOffset))
                    .add(0, img.yOffset, 0);
            matrices.push();
            matrices.translate(worldPos.x - targetPos.x, worldPos.y - targetPos.y, worldPos.z - targetPos.z);
            float backYaw = player.getYaw() + 180;
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-backYaw));
            if (img.selfRotateZ != 0) {
                float angle = (player.getWorld().getTime() * img.selfRotateZ) % 360;
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(angle));
            }
            float halfW = img.width / 2;
            float halfH = img.height / 2;
            RenderLayer layer = RenderLayer.getEntityTranslucent(img.texture);
            VertexConsumer vertex = consumers.getBuffer(layer);
            Matrix4f posMat = matrices.peek().getPositionMatrix();
            int light = WorldRenderer.getLightmapCoordinates(player.getWorld(), player.getBlockPos());
            int overlay = OverlayTexture.DEFAULT_UV;
            float nx = (float) backDir.x, ny = 0, nz = (float) backDir.z;
            vertex.vertex(posMat, -halfW, -halfH, 0).color(255,255,255,255).texture(0,0).overlay(overlay).light(light).normal(nx,ny,nz);
            vertex.vertex(posMat, -halfW, halfH, 0).color(255,255,255,255).texture(0,1).overlay(overlay).light(light).normal(nx,ny,nz);
            vertex.vertex(posMat, halfW, halfH, 0).color(255,255,255,255).texture(1,1).overlay(overlay).light(light).normal(nx,ny,nz);
            vertex.vertex(posMat, halfW, -halfH, 0).color(255,255,255,255).texture(1,0).overlay(overlay).light(light).normal(nx,ny,nz);
            matrices.pop();
        }
        matrices.pop();
    }
}
