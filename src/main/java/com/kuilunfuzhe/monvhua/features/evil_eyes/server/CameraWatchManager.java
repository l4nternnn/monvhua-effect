package com.kuilunfuzhe.monvhua.features.evil_eyes.server;

import com.kuilunfuzhe.monvhua.event.tag_pitch;
import com.kuilunfuzhe.monvhua.features.evil_eyes.Evil_Eyes;
import com.kuilunfuzhe.monvhua.network.camerawatch.CameraUpdateS2CPacket;
import com.kuilunfuzhe.monvhua.network.camerawatch.CameraWatchUnbindS2CPacket;
import com.kuilunfuzhe.monvhua.network.evil_eyes.EvilEyesPackets.ClairvoyanceEnergyS2C;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端第三人称相机系统 —— 在观看模式下将客户端相机绑定到目标实体背后。
 * <p>
 * 核心机制：
 * <ul>
 *   <li>相机默认位于目标实体背后方向（基于yaw计算），距离可配置（默认4格）</li>
 *   <li>多级平滑插值：yaw平滑（0.5系数）、距离平滑（0.15系数）、位置平滑（lerp 0.35系数），防止画面抖动</li>
 *   <li>射线碰撞检测：相机与目标之间有方块遮挡时自动缩短距离，防止穿墙</li>
 *   <li>每秒20次更新频率（由服务端 tick 驱动），通过 {@link CameraUpdateS2CPacket} 下发相机位姿</li>
 * </ul>
 */
public class CameraWatchManager {
    /** 活跃观看会话：观看者UUID → CameraSession */
    private static final Map<UUID, CameraSession> SESSIONS = new ConcurrentHashMap<>();
    /** 平滑后的实体yaw值：观看者UUID → 平滑yaw（用于相机跟随目标旋转） */
    private static final Map<UUID, Float> smoothYaws = new ConcurrentHashMap<>();
    /** 上一帧的相机位置：观看者UUID → Vec3d（用于帧间平滑插值） */
    private static final Map<UUID, Vec3d> prevCamPos = new ConcurrentHashMap<>();
    /** 上一帧的有效相机距离：观看者UUID → Double（用于碰撞距离平滑） */
    private static final Map<UUID, Double> prevCamDistance = new ConcurrentHashMap<>();
    private static final double MAX_ENERGY = 100.0D;
    private static final Map<UUID, Double> playerEnergy = new ConcurrentHashMap<>();
    private static final Map<UUID, UiState> UI_STATES = new ConcurrentHashMap<>();
    private static int energySyncTick;

    /** 观看会话记录：包含被观看实体UUID和会话开始游戏刻 */
    private record CameraSession(UUID targetUuid, long startTick) {}
    private record UiState(boolean open, int previewCount, boolean hovered, boolean expanded) {}
    private record EnergyRates(double uiDrainRate, double watchDrainRate, double regenRate) {}

    /**
     * 判断玩家是否正处于服务端相机观看模式中。
     *
     * @param player 目标玩家
     * @return 是否在观看某个实体
     */
    public static boolean isWatching(ServerPlayerEntity player) {
        return SESSIONS.containsKey(player.getUuid());
    }

    public static void setUiState(ServerPlayerEntity player, boolean open, int previewCount, boolean hovered, boolean expanded) {
        UUID uuid = player.getUuid();
        playerEnergy.putIfAbsent(uuid, MAX_ENERGY);
        if (!open) {
            UI_STATES.remove(uuid);
            return;
        }
        UI_STATES.put(uuid, new UiState(true, MathHelper.clamp(previewCount, 0, 4), hovered, expanded));
    }

    /**
     * 启动服务端相机观看 —— 将相机绑定到目标实体背后。
     * 如果已有活跃会话则先停止，再创建新会话。
     *
     * @param viewer     观看者玩家
     * @param targetUuid 被观看实体的UUID
     * @param server     服务端实例
     */
    public static void startWatching(ServerPlayerEntity viewer, UUID targetUuid, MinecraftServer server) {
        ServerWorld world = viewer.getWorld();
        Entity target = world.getEntity(targetUuid);
        if (target == null || !target.isAlive()) {
            viewer.sendMessage(Text.literal("§c目标不存在或已死亡"), true);
            return;
        }
        if (!Evil_Eyes.canMarkTarget(target)) {
            viewer.sendMessage(Text.literal("搂c鐩爣涓嶅彲琚崈閲岀溂瑙傜湅"), true);
            stopWatching(viewer, server);
            return;
        }
        stopWatching(viewer, server);
        SESSIONS.put(viewer.getUuid(), new CameraSession(targetUuid, world.getTime()));
        playerEnergy.putIfAbsent(viewer.getUuid(), MAX_ENERGY);
        ClairvoyanceChunkLoader.startWatching(viewer.getUuid(), world, target.getPos(), world.getTime());
        viewer.sendMessage(Text.literal("§a正在观看 " + tag_pitch.entityDisplayName(target)), true);
    }

    /**
     * 停止服务端相机观看 —— 移除会话记录和平滑状态，发送解除绑定包。
     *
     * @param viewer 观看者玩家
     * @param server 服务端实例
     */
    public static void stopWatching(ServerPlayerEntity viewer, MinecraftServer server) {
        UUID uuid = viewer.getUuid();
        SESSIONS.remove(uuid);
        smoothYaws.remove(uuid);
        prevCamPos.remove(uuid);
        prevCamDistance.remove(uuid);
        ClairvoyanceChunkLoader.stopWatching(uuid, viewer.getWorld().getTime());
        ServerPlayNetworking.send(viewer, new CameraWatchUnbindS2CPacket());
    }

    /**
     * 每服务端 tick 调用一次，遍历所有活跃观看会话并更新各自的相机位姿。
     * 由 {@code Evil_Eyes} 或外部 tick 循环调用。
     *
     * @param server 服务端实例
     */
    public static void tick(MinecraftServer server) {
        for (Map.Entry<UUID, CameraSession> entry : SESSIONS.entrySet()) {
            ServerPlayerEntity viewer = server.getPlayerManager().getPlayer(entry.getKey());
            if (viewer == null) {
                SESSIONS.remove(entry.getKey());
                ClairvoyanceChunkLoader.stopWatching(entry.getKey(), server.getOverworld().getTime());
                continue;
            }
            updateCameraForViewer(viewer, server);
        }
        tickEnergy(server);
        ClairvoyanceChunkLoader.tick(server);
    }

    private static void tickEnergy(MinecraftServer server) {
        energySyncTick++;
        boolean sync = energySyncTick >= 5;
        if (sync) {
            energySyncTick = 0;
        }
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID uuid = player.getUuid();
            double energy = playerEnergy.getOrDefault(uuid, MAX_ENERGY);
            EnergyRates rates = energyRates(player);
            UiState uiState = UI_STATES.get(uuid);
            double drainRate = 0.0D;
            if (uiState != null && uiState.open()) {
                drainRate += rates.uiDrainRate();
                if (uiState.hovered() && uiState.previewCount() > 0) {
                    drainRate += rates.uiDrainRate();
                }
                if (uiState.expanded()) {
                    drainRate += rates.watchDrainRate();
                }
            }
            if (isWatching(player)) {
                drainRate += rates.watchDrainRate();
            }

            if (drainRate > 0.0D) {
                energy = Math.max(0.0D, energy - drainRate / 20.0D);
                playerEnergy.put(uuid, energy);
                if (energy <= 0.0D) {
                    UI_STATES.remove(uuid);
                    Evil_Eyes.forceStopWatching(player, server);
                    player.sendMessage(Text.literal("搂8鍗冮噷鐪艰兘閲忚€楀敖"), true);
                }
            } else {
                energy = Math.min(MAX_ENERGY, energy + rates.regenRate() / 20.0D);
                playerEnergy.put(uuid, energy);
            }
            if (sync) {
                ServerPlayNetworking.send(player, new ClairvoyanceEnergyS2C(energy, MAX_ENERGY));
            }
        }
        UI_STATES.keySet().removeIf(uuid -> server.getPlayerManager().getPlayer(uuid) == null);
    }

    private static EnergyRates energyRates(ServerPlayerEntity player) {
        if (Evil_Eyes.configManager == null) {
            return new EnergyRates(1.0D, 8.0D, 2.0D);
        }
        int stage = Evil_Eyes.getPlayerStage(player, Evil_Eyes.configManager);
        var config = Evil_Eyes.configManager.getStageConfig(stage);
        return new EnergyRates(
                Math.max(0.0D, config.uiDrainRate()),
                Math.max(0.0D, config.watchDrainRate()),
                Math.max(0.0D, config.regenRate())
        );
    }

    /**
     * 为单个观看者更新相机位姿 —— 多级平滑插值 + 射线碰撞检测。
     * <p>
     * 流程：
     * <ol>
     *   <li>yaw平滑：当前yaw + wrapDegrees(目标yaw-当前yaw) * 0.5，消除角度跳变</li>
     *   <li>计算理想相机终点（目标背后，距离可配置）</li>
     *   <li>射线检测碰撞：有遮挡则缩短距离（最小0.5格）</li>
     *   <li>距离平滑：prevDist + (rawDist - prevDist) * 0.15</li>
     *   <li>位置平滑：prevPos.lerp(rawPos, 0.35)</li>
     *   <li>计算相机yaw/pitch使镜头始终朝向实体眼睛</li>
     * </ol>
     */
    private static void updateCameraForViewer(ServerPlayerEntity viewer, MinecraftServer server) {
        CameraSession session = SESSIONS.get(viewer.getUuid());
        if (session == null) return;
        ServerWorld world = viewer.getWorld();
        Entity target = world.getEntity(session.targetUuid);
        if (target == null || !target.isAlive()) {
            stopWatching(viewer, server);
            Evil_Eyes.forceStopWatching(viewer, server);
            viewer.sendMessage(Text.literal("§c目标已消失"), true);
            return;
        }

        ClairvoyanceChunkLoader.update(viewer.getUuid(), world, target.getPos(), world.getTime());

        CameraOffset offset = getOffset(viewer);
        double distance = offset.distance;
        UUID viewerId = viewer.getUuid();

        // yaw平滑插值：0.5系数使相机跟随目标旋转时不会突变
        float targetYaw = target.getYaw();
        float prevSmoothYaw = smoothYaws.getOrDefault(viewerId, targetYaw);
        float smoothYaw = prevSmoothYaw + MathHelper.wrapDegrees(targetYaw - prevSmoothYaw) * 0.5f;
        smoothYaws.put(viewerId, smoothYaw);

        // 实体视线方向: (-sin(yaw), 0, cos(yaw))，背后=反方向=(sin(yaw), 0, -cos(yaw))
        Vec3d targetPos = target.getPos();
        double radYaw = Math.toRadians(smoothYaw);
        double bx = Math.sin(radYaw);   // 背后方向的x分量
        double bz = -Math.cos(radYaw);  // 背后方向的z分量
        Vec3d targetEye = target.getEyePos();
        Vec3d idealEnd = targetPos.add(bx * distance, 1.5, bz * distance); // 相机设在目标脚底+1.5格高度

        // 射线检测（从实体眼睛到摄像机位置）
        RaycastContext ctx = new RaycastContext(targetEye, idealEnd,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, target);
        BlockHitResult hit = world.raycast(ctx);

        // 射线碰撞处理：有遮挡则取碰撞点距离-0.2格缓冲，下限0.5格防止相机贴脸
        double rawDist;
        if (hit.getType() == HitResult.Type.BLOCK) {
            double hitDist = hit.getPos().distanceTo(targetEye);
            rawDist = Math.max(0.5, hitDist - 0.2); // 离墙保留0.2格缓冲
        } else {
            rawDist = distance;
        }
        // 距离平滑插值：0.15系数使碰撞导致的距离变化平缓过渡
        Double prevDist = prevCamDistance.get(viewerId);
        if (prevDist != null) {
            rawDist = prevDist + (rawDist - prevDist) * 0.15;
        }
        rawDist = Math.max(0.5, rawDist); // 无论如何不低于0.5格
        prevCamDistance.put(viewerId, rawDist);

        // 计算理想相机位置（使用平滑后的距离，高度=目标脚底+1.5格）
        Vec3d rawCamPos = targetPos.add(bx * rawDist, 1.5, bz * rawDist);

        // 位置平滑插值：lerp系数0.35使相机移动柔和，消除目标急转/瞬移时的画面抖动
        Vec3d prevPos = prevCamPos.get(viewerId);
        Vec3d camPos;
        if (prevPos != null) {
            camPos = prevPos.lerp(rawCamPos, 0.35);
        } else {
            camPos = rawCamPos;
        }
        prevCamPos.put(viewerId, camPos);

        // 摄像机始终看向实体眼睛位置
        double dx = targetEye.x - camPos.x;
        double dy = targetEye.y - camPos.y;
        double dz = targetEye.z - camPos.z;
        // atan2(dz,dx)得出从相机指向目标的角度，-90°转换为Minecraft yaw坐标系（0°=正南）
        float camYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
        camYaw = MathHelper.wrapDegrees(camYaw);
        // pitch：俯仰角，负号使向上看为正，clamp限制-90°~90°避免翻转
        float camPitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
        camPitch = MathHelper.clamp(camPitch, -90, 90);

        ServerPlayNetworking.send(viewer, new CameraUpdateS2CPacket(camPos, camYaw, camPitch));
    }

    /** 玩家个性化相机偏移存储：玩家UUID → CameraOffset */
    private static final Map<UUID, CameraOffset> PLAYER_OFFSETS = new ConcurrentHashMap<>();

    /** 相机偏移参数：控制相机相对于目标的俯仰、偏航和距离 */
    public static class CameraOffset {
        /** 水平偏航偏移（度） */
        public float yaw;
        /** 垂直俯仰偏移（度） */
        public float pitch;
        /** 相机与目标的距离（格），默认4.0 */
        public double distance;

        public CameraOffset(float yaw, float pitch, double distance) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.distance = distance;
        }
    }

    /**
     * 设置玩家的相机偏移参数。
     *
     * @param player   目标玩家
     * @param yaw      水平偏航偏移（度）
     * @param pitch    垂直俯仰偏移（度）
     * @param distance 相机距离（格）
     */
    public static void setOffset(ServerPlayerEntity player, float yaw, float pitch, double distance) {
        PLAYER_OFFSETS.put(player.getUuid(), new CameraOffset(yaw, pitch, distance));
    }

    /**
     * 重置玩家的相机偏移为默认值（距离4.0格，无偏航俯仰偏移）。
     *
     * @param player 目标玩家
     */
    public static void resetOffset(ServerPlayerEntity player) {
        PLAYER_OFFSETS.remove(player.getUuid());
    }

    private static CameraOffset getOffset(ServerPlayerEntity player) {
        return PLAYER_OFFSETS.getOrDefault(player.getUuid(),
                new CameraOffset(0f, 0f, 4.0f));
    }
}
