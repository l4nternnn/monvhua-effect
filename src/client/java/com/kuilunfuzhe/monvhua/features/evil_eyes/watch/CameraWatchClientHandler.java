package com.kuilunfuzhe.monvhua.features.evil_eyes.watch;

import com.kuilunfuzhe.monvhua.features.evil_eyes.Evil_EyesClient;
import com.kuilunfuzhe.monvhua.network.SafeClientNetworking;
import com.kuilunfuzhe.monvhua.network.camerawatch.CameraWatchStopC2SPacket;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * 多人联机摄像机观看客户端处理器。
 * 通过指数插值平滑跟随服务器推送的摄像机位置和朝向，
 * 支持Shift键退出观看模式（上升沿触发防重复发送）。
 */
public class CameraWatchClientHandler {

    /** 充当虚拟摄像机的实体（匿名ArmorStand子类） */
    private static Entity dummyCamera = null;
    /** 服务器推送的目标摄像机位置 */
    private static Vec3d targetPos = null;
    /** 服务器推送的目标偏航角和俯仰角 */
    private static float targetYaw = 0, targetPitch = 0;
    /** 是否拥有有效目标（用于判断是否处于观看模式） */
    private static boolean hasValidTarget = false;
    /** 上一tick的潜行状态，用于上升沿检测 */
    private static boolean lastSneak = false;

    /**
     * 接收服务器推送的摄像机位置更新。
     * 存储目标坐标用于后续逐帧平滑插值。
     *
     * @param pos   目标位置
     * @param yaw   目标偏航角
     * @param pitch 目标俯仰角
     */
    public static void onCameraUpdate(Vec3d pos, float yaw, float pitch) {
        if (!hasValidTarget) {
            Evil_EyesClient.exitViewMode(MinecraftClient.getInstance());
        }
        targetPos = pos;
        targetYaw = yaw;
        targetPitch = pitch;
        hasValidTarget = true;
    }

    /**
     * 解除摄像机绑定，清空所有状态并将相机归还给玩家。
     */
    public static void onUnbind() {
        dummyCamera = null;
        hasValidTarget = false;
        targetPos = null;
        lastSneak = false;
        MinecraftClient.getInstance().cameraEntity = MinecraftClient.getInstance().player;
    }

    /**
     * 判断当前是否处于活跃的摄像机观看模式。
     * @return true表示正在观看
     */
    public static boolean isActive() {
        return hasValidTarget && dummyCamera != null;
    }

    /** 注册客户端tick事件，实现平滑跟随和Shift退出 */
    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null || client.player == null) return;

            // Shift 退出观看（上升沿触发，防重复发送）
            if (hasValidTarget && client.player.isSneaking() && !lastSneak) {
                lastSneak = true;
                SafeClientNetworking.send(new CameraWatchStopC2SPacket());
                return;
            }
            lastSneak = client.player.isSneaking();

            if (!hasValidTarget || targetPos == null) return;

            if (dummyCamera == null) {
                dummyCamera = new Entity(EntityType.ARMOR_STAND, client.world) {
                    @Override protected void initDataTracker(DataTracker.Builder builder) {}
                    @Override public boolean damage(ServerWorld world, DamageSource source, float amount) { return false; }
                    @Override protected void readCustomData(ReadView view) {}
                    @Override protected void writeCustomData(WriteView view) {}
                };
                dummyCamera.setInvisible(true);
                dummyCamera.refreshPositionAndAngles(targetPos.x, targetPos.y, targetPos.z, targetYaw, targetPitch);
                client.cameraEntity = dummyCamera;
                return;
            }

            Vec3d current = dummyCamera.getPos();
            // 指数插值平滑：位置插值系数0.7，旋转插值系数0.6，产生渐进跟随效果
            double nx = current.x + (targetPos.x - current.x) * 0.7;
            double ny = current.y + (targetPos.y - current.y) * 0.7;
            double nz = current.z + (targetPos.z - current.z) * 0.7;
            float nyaw = dummyCamera.getYaw() + MathHelper.wrapDegrees(targetYaw - dummyCamera.getYaw()) * 0.6f;
            float npitch = dummyCamera.getPitch() + MathHelper.clamp(targetPitch - dummyCamera.getPitch(), -180, 180) * 0.6f;

            dummyCamera.refreshPositionAndAngles(nx, ny, nz, nyaw, npitch);

            if (client.cameraEntity != dummyCamera) {
                client.cameraEntity = dummyCamera;
            }
        });
    }
}
