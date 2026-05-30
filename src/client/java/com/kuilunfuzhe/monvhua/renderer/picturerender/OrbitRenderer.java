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

/**
 * 环形轨道旋转图片渲染器。
 * 在玩家周围以圆形轨道排列多张装饰贴图，贴图围绕玩家旋转并面向相机。
 * 特殊玩家在阶段7可看到全部8张图片，普通玩家仅显示间隔的4张（索引0,2,4,6）。
 */
public class OrbitRenderer {

    /**
     * 轨道图片参数记录。
     * @param texture         贴图资源标识符
     * @param radius          轨道半径（距玩家中心的水平距离）
     * @param speed           轨道旋转速度（数值越大旋转越快，结合angleBase计算实际角速度）
     * @param yOffset         垂直偏移（相对于玩家中心的高度）
     * @param selfRotateSpeed 图片自旋速度（每tick旋转的度数，基于gameTime/20.0换算）
     * @param startAngle      初始角度偏移（度数），用于在圆周上均匀分布多张图片
     */
    private record OrbitImage(Identifier texture, double radius, double speed, double yOffset, float selfRotateSpeed, double startAngle) {}

    /** 预定义的8张轨道旋转图片，交替使用两种纹理，起始角度均匀分布（间隔45度） */
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

    /**
     * 渲染环绕玩家的轨道旋转图片。
     * 仅当玩家处于阶段7时生效，通过gameTime驱动旋转动画。
     *
     * @param matrices          矩阵栈
     * @param consumers         顶点消费者提供者
     * @param player            目标玩家
     * @param currentPlayerStage 当前玩家的WitchStage阶段
     */
    public static void render(MatrixStack matrices, VertexConsumerProvider consumers, PlayerEntity player, int currentPlayerStage) {
        if (!BackTextureRenderer.imagesEnabled) return;

        // 根据玩家身份和阶段决定渲染哪些图片
        String playerName = player.getName().getString();
        boolean isSpecial = BackTextureRenderer.SPECIAL_NAMES.contains(playerName);
        List<OrbitImage> imagesToRender = new ArrayList<>();
        if (isSpecial && currentPlayerStage == 7) {
            // 特殊玩家阶段7：渲染全部8张图片
            imagesToRender.addAll(ORBIT_IMAGES);
        } else if (currentPlayerStage == 7 && !ORBIT_IMAGES.isEmpty()) {
            // 普通玩家阶段7：仅渲染间隔的4张（索引0,2,4,6）
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

        // 轨道圆心：玩家位置 + 半身高，即玩家身体中心
        Vec3d targetPos = player.getPos().add(0, player.getHeight() / 2, 0);
        Vec3d center = player.getPos().add(0, player.getHeight() * 0.5, 0);

        // 基于游戏时间的角度基准：每秒（20tick）旋转360度，即完整一圈/秒
        long gameTime = player.getWorld().getTime();
        double angleBase = (gameTime / 20.0) * 360;

        for (OrbitImage img : imagesToRender) {
            // 计算当前角度：基础角度 × 速度比例 + 起始偏移
            double angle = angleBase * (img.speed / 360.0) + img.startAngle;
            double rad = Math.toRadians(angle);

            // 通过三角函数计算轨道上的水平位置
            double x = img.radius * Math.cos(rad);
            double z = img.radius * Math.sin(rad);
            Vec3d worldPos = center.add(x, img.yOffset, z);

            // 根据世界坐标计算光照，使图片融入环境光
            int light = WorldRenderer.getLightmapCoordinates(client.world, BlockPos.ofFloored(worldPos));
            int overlay = OverlayTexture.DEFAULT_UV;

            // 每张图片大小为0.2格见方
            float size = 0.2f;
            float half = size / 2;

            matrices.push();
            // 平移到图片的世界坐标（相对于相机）
            matrices.translate(worldPos.x - targetPos.x, worldPos.y - targetPos.y, worldPos.z - targetPos.z);

            // 计算面向相机的旋转角度，使贴图始终正对玩家视角
            float yaw = (float) Math.toDegrees(Math.atan2(targetPos.z - worldPos.z, targetPos.x - worldPos.x)) + 90;
            float pitch = (float) Math.toDegrees(Math.atan2(targetPos.y - worldPos.y,
                    Math.hypot(targetPos.x - worldPos.x, targetPos.z - worldPos.z)));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));

            // 图片自旋：基于游戏时间和自旋速度，绕Z轴旋转
            float selfAngle = (float) ((gameTime * img.selfRotateSpeed / 20.0) % 360);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(selfAngle));

            RenderLayer layer = RenderLayer.getEntityTranslucent(img.texture);
            VertexConsumer vertex = consumers.getBuffer(layer);
            Matrix4f posMat = matrices.peek().getPositionMatrix();

            // 半透明绘制（alpha=128），左下→左上→右上→右下
            vertex.vertex(posMat, -half, -half, 0).color(255,255,255,128).texture(0,0).overlay(overlay).light(light).normal(0,0,1);
            vertex.vertex(posMat, -half, half, 0).color(255,255,255,128).texture(0,1).overlay(overlay).light(light).normal(0,0,1);
            vertex.vertex(posMat, half, half, 0).color(255,255,255,128).texture(1,1).overlay(overlay).light(light).normal(0,0,1);
            vertex.vertex(posMat, half, -half, 0).color(255,255,255,128).texture(1,0).overlay(overlay).light(light).normal(0,0,1);
            matrices.pop();
        }
    }
}
