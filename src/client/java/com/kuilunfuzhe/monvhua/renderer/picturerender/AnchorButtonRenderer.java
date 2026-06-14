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

/**
 * 锚点按钮渲染器，在锚点实体（盔甲架）上方渲染面向相机的按钮贴图。
 * 使用球面定位将按钮偏移到距离锚点中心一小段半径的位置，使按钮始终浮在实体前方朝向玩家。
 */
public class AnchorButtonRenderer {

    /** 锚点信息，记录锚点实体的世界坐标和最后可见时间 */
    public static class AnchorInfo {
        /** 锚点实体的世界坐标 */
        public Vec3d pos;
        /** 最后被观察到的时间戳，用于超时清理 */
        public long lastSeenTime;
    }

    /** 所有活跃锚点的映射表，键为锚点实体的UUID，线程安全 */
    public static final Map<UUID, AnchorInfo> anchors = new ConcurrentHashMap<>();

    /** 球面定位半径，控制按钮距离锚点中心的偏移距离，值越小按钮越贴近中心 */
    private static final double SPHERE_RADIUS = 0.2;

    /**
     * 渲染所有活跃的锚点按钮。
     * 对每个锚点执行：球面定位计算世界坐标 → 面向相机旋转 → 绘制半透明贴图。
     *
     * @param matrices  矩阵栈，用于累积变换
     * @param consumers 顶点消费者提供者，用于获取渲染缓冲区
     */
    public static void render(MatrixStack matrices, VertexConsumerProvider consumers) {
        if (anchors.isEmpty()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;
        Camera camera = client.gameRenderer.getCamera();
        Vec3d targetPos = camera.getPos();
        Identifier buttonTexture = Identifier.of("monvhua", "textures/gui/yj5.png");
        float size = 1.0f;
        float half = size / 2;
        int overlay = OverlayTexture.DEFAULT_UV;

        // 获取玩家视线位置作为球面定位的参考点
        Vec3d playerChest = client.player.getEyePos();
        // 按钮相对于盔甲架底座的中心偏移：XZ居中，Y向下0.4格
        double offsetX = 0.0, offsetY = -0.4, offsetZ = 0.0;
        Iterator<Map.Entry<UUID, AnchorInfo>> it = anchors.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, AnchorInfo> entry = it.next();
            AnchorInfo info = entry.getValue();
            Vec3d armorStandPos = info.pos;

            // 球面定位：计算玩家到锚点中心的方向，将按钮沿该方向推出SPHERE_RADIUS距离
            Vec3d center = armorStandPos.add(offsetX, offsetY, offsetZ);
            Vec3d dir = playerChest.subtract(center).normalize();
            Vec3d worldPos = center.add(dir.multiply(SPHERE_RADIUS));

            // 全亮度光照（blockLight=15, skyLight=15），使按钮在任何环境下都清晰可见
            int light = 0xF000F0;

            matrices.push();
            // 平移到按钮的世界坐标（相对于相机）
            matrices.translate(worldPos.x - targetPos.x, worldPos.y - targetPos.y, worldPos.z - targetPos.z);

            // 计算面向相机的旋转角度：yaw水平旋转 +90度修正，pitch垂直旋转
            float yaw = (float) Math.toDegrees(Math.atan2(targetPos.z - worldPos.z, targetPos.x - worldPos.x)) + 90;
            float pitch = (float) Math.toDegrees(Math.atan2(targetPos.y - worldPos.y,
                    Math.hypot(targetPos.x - worldPos.x, targetPos.z - worldPos.z)));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));

            // 使用实体半透明图层渲染按钮贴图
            RenderLayer layer = RenderLayer.getEntityTranslucent(buttonTexture);
            VertexConsumer vertex = consumers.getBuffer(layer);
            Matrix4f posMat = matrices.peek().getPositionMatrix();

            // 绘制四边形：按逆时针顺序（右上→右下→左下→左上）
            vertex.vertex(posMat, half, half, 0).color(255,255,255,255).texture(0,0).overlay(overlay).light(light).normal(0,0,1);
            vertex.vertex(posMat, half, -half, 0).color(255,255,255,255).texture(0,1).overlay(overlay).light(light).normal(0,0,1);
            vertex.vertex(posMat, -half, -half, 0).color(255,255,255,255).texture(1,1).overlay(overlay).light(light).normal(0,0,1);
            vertex.vertex(posMat, -half, half, 0).color(255,255,255,255).texture(1,0).overlay(overlay).light(light).normal(0,0,1);
            matrices.pop();
        }
    }
}
