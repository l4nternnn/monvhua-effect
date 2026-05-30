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

/**
 * 玩家背后装饰图片渲染器。
 * 在符合条件的玩家背后渲染多张装饰性贴图，支持位置偏移和自旋动画。
 * 特殊玩家（SPECIAL_NAMES中的玩家）在阶段7可渲染全部4张图片，普通玩家仅渲染第一张。
 */
public class BackTextureRenderer {

    /** 渲染总开关，由外部控制开启/关闭 */
    public static boolean imagesEnabled = false;

    /** 特殊玩家名集合，这些玩家在阶段7时可以看到全部背后图片 */
    public static final Set<String> SPECIAL_NAMES = Set.of("shushuwonie", "Remio");

    /**
     * 背后图片参数记录。
     * @param texture      贴图资源标识符
     * @param xOffset      左右偏移量（以玩家右方向为正）
     * @param yOffset      上下偏移量
     * @param zOffset      前后偏移量（以玩家背后方向为正）
     * @param width        贴图宽度
     * @param height       贴图高度
     * @param selfRotateZ  绕Z轴自旋速度（每tick旋转的度数，0表示不自旋）
     */
    private record BackImage(Identifier texture, float xOffset, float yOffset, float zOffset, float width, float height, float selfRotateZ) {}

    /** 预定义的4张背后装饰图片及其位置/动画参数 */
    private static final List<BackImage> BACK_IMAGES = new ArrayList<>();
    static {
        BACK_IMAGES.add(new BackImage(Identifier.of("monvhua", "textures/gui/6.png"), 0.0f, 1.0f, 3.0f, 3.5f, 3.5f, 1.0f));
        BACK_IMAGES.add(new BackImage(Identifier.of("monvhua", "textures/gui/3.png"), -2f, 0.8f, 3.0f, 2.0f, 2.0f, -1.5f));
        BACK_IMAGES.add(new BackImage(Identifier.of("monvhua", "textures/gui/3.png"), 2f, 0.8f, 3.0f, 2.0f, 2.0f, -1.5f));
        BACK_IMAGES.add(new BackImage(Identifier.of("monvhua", "textures/gui/11.png"), 0.0f, 3f, 4.0f, 2.0f, 2.0f, 3.0f));
    }

    /**
     * 渲染玩家背后的装饰图片。
     * 仅当玩家手持有效物品（千里眼/魔法棒/保密物品）且处于阶段7时生效。
     *
     * @param matrices          矩阵栈
     * @param consumers         顶点消费者提供者
     * @param player            目标玩家
     * @param currentPlayerStage 当前玩家的WitchStage阶段
     */
    public static void render(MatrixStack matrices, VertexConsumerProvider consumers, PlayerEntity player, int currentPlayerStage) {
        if (!imagesEnabled) return;

        // 检查玩家手持物品：千里眼物品、魔法棒或保密物品
        ItemStack mainHand = player.getMainHandStack();
        boolean hasValidItem = mainHand.getItem() == Evil_Eyes.CLAIRVOYANCE_ITEM || mainHand.getItem() == ModItems.MAGIC_STICK || SecrecyItem.isHoldingSecrecy(mainHand);
        if (!hasValidItem) return;

        // 根据玩家身份和阶段决定渲染哪些图片
        String playerName = player.getName().getString();
        boolean isSpecial = SPECIAL_NAMES.contains(playerName);
        List<BackImage> imagesToRender = new ArrayList<>();
        if (isSpecial && currentPlayerStage == 7) {
            // 特殊玩家阶段7：渲染全部4张图片
            imagesToRender.addAll(BACK_IMAGES);
        } else if (currentPlayerStage == 7 && !BACK_IMAGES.isEmpty()) {
            // 普通玩家阶段7：仅渲染第一张（中间大图）
            imagesToRender.add(BACK_IMAGES.get(0));
        }
        if (imagesToRender.isEmpty()) return;

        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
        Vec3d targetPos = camera.getPos();

        // 计算玩家面朝方向的反方向和右方向，用于定位背后图片
        Vec3d forward = player.getRotationVec(1.0f);
        Vec3d backDir = forward.multiply(-1);
        Vec3d rightDir = new Vec3d(forward.z, 0, -forward.x).normalize();

        matrices.push();
        for (BackImage img : imagesToRender) {
            // 计算图片世界坐标：玩家位置 + 背后偏移 + 左右偏移 + 上下偏移
            Vec3d worldPos = player.getPos()
                    .add(backDir.multiply(img.zOffset))
                    .add(rightDir.multiply(img.xOffset))
                    .add(0, img.yOffset, 0);

            matrices.push();
            // 平移到图片位置（相对于相机）
            matrices.translate(worldPos.x - targetPos.x, worldPos.y - targetPos.y, worldPos.z - targetPos.z);

            // 使图片面向相机方向（玩家朝向+180度即背后方向）
            float backYaw = player.getYaw() + 180;
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-backYaw));

            // 图片绕Z轴自旋动画（基于世界时间累积角度）
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

            // 法线方向使用背后方向（水平分量），使图片正确受光
            float nx = (float) backDir.x, ny = 0, nz = (float) backDir.z;

            // 绘制四边形：左下→左上→右上→右下
            vertex.vertex(posMat, -halfW, -halfH, 0).color(255,255,255,255).texture(0,0).overlay(overlay).light(light).normal(nx,ny,nz);
            vertex.vertex(posMat, -halfW, halfH, 0).color(255,255,255,255).texture(0,1).overlay(overlay).light(light).normal(nx,ny,nz);
            vertex.vertex(posMat, halfW, halfH, 0).color(255,255,255,255).texture(1,1).overlay(overlay).light(light).normal(nx,ny,nz);
            vertex.vertex(posMat, halfW, -halfH, 0).color(255,255,255,255).texture(1,0).overlay(overlay).light(light).normal(nx,ny,nz);
            matrices.pop();
        }
        matrices.pop();
    }
}
