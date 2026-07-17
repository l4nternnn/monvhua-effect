package com.kuilunfuzhe.monvhua.features.evil_eyes;

import com.kuilunfuzhe.monvhua.event.tag_pitch;
import com.kuilunfuzhe.monvhua.gui.evil_eyes.Evil_eyesScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.mojang.text2speech.Narrator.LOGGER;

/**
 * 千里眼客户端核心逻辑。
 * 负责创建观察视角的盔甲架实体、更新相机位置（含射线检测避障）、
 * 让相机注视目标实体，并提供视角进入/退出管理。
 */
public class Evil_EyesClient {
    /** 当前正在观察的目标实体UUID */
    private static UUID currentViewingEntity = null;
    /** 作为虚拟相机的隐形盔甲架实体 */
    private static ArmorStandEntity cameraEntity = null;
    /** UI打开时间戳（tick计数） */
    private static long uiOpenTime = 0;
    /** UI最大存活tick数（1200 tick = 60秒） */
    private static final long UI_MAX_TICKS = 1200;
    /** 是否正在观察模式 */
    private static boolean isViewing = false;
    private static boolean lastRightClickState = false;
    private static long lastRightClickTime = 0;
    /** 右键防抖延迟（10 tick = 0.5秒） */
    private static final long RIGHT_CLICK_DELAY_TICKS = 10;
    /** 相机距离目标的固定距离（3格） */
    private static final double CAM_DISTANCE = 3.0;
    /** 相机相对目标的基础偏航角（45度） */
    private static final float CAM_YAW = 45.0f;
    /** 相机相对目标的基础俯仰角（30度） */
    private static final float CAM_PITCH = 30.0f;

    // 公开的标记实体映射（供其他类使用）
    public static final Map<UUID, Long> localMarkedEntities = new ConcurrentHashMap<>();
    public static final Map<UUID, MarkedEntityName> localMarkedEntityNames = new ConcurrentHashMap<>();
    public static final Map<UUID, AnchorEntry> localAnchors = new ConcurrentHashMap<>();
    public static final Map<UUID, SignedItemEntry> localSignedItems = new ConcurrentHashMap<>();
    public static final Map<UUID, CooldownEntry> localUnmarkCooldowns = new ConcurrentHashMap<>();
    private static final long LOST_TARGET_GRACE_TICKS = 40L;
    private static final Map<UUID, Long> missingEntitySince = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> missingSyncSince = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> markedSyncGenerations = new ConcurrentHashMap<>();
    private static long currentMarkedSyncGeneration = 0L;
    private static long pendingMarkedSyncGeneration = -1L;
    private static long pendingMarkedSyncTick = -1L;
    private static volatile String viewMode = "viewport";

    public record MarkedEntityName(String name, String tag) {
    }

    public record AnchorEntry(Vec3d pos, String label) {
    }

    public record SignedItemEntry(String label) {
    }

    public record CooldownEntry(String name, long expireTick) {
    }

    public static void setViewMode(String mode) {
        viewMode = mode == null || mode.isBlank() ? "viewport" : mode;
        if (!isViewportMode()) {
            ClairvoyanceViewportRenderer.cleanup();
        }
    }

    public static String getViewMode() {
        return viewMode;
    }

    public static boolean isViewportMode() {
        return "viewport".equals(viewMode);
    }

    public static void beginMarkedListSync(MinecraftClient client) {
        long now = client != null && client.world != null ? client.world.getTime() : System.currentTimeMillis() / 50L;
        currentMarkedSyncGeneration++;
        pendingMarkedSyncGeneration = currentMarkedSyncGeneration;
        pendingMarkedSyncTick = now;
    }

    public static void receiveMarkedEntity(UUID uuid, long expire, String name, String tag) {
        localMarkedEntities.put(uuid, expire);
        localMarkedEntityNames.put(uuid, new MarkedEntityName(name, tag));
        markedSyncGenerations.put(uuid, currentMarkedSyncGeneration);
        missingEntitySince.remove(uuid);
        missingSyncSince.remove(uuid);
    }

    public static void receiveAnchorList(boolean clear, UUID standId, Vec3d pos, String label) {
        if (clear) {
            localAnchors.clear();
        }
        if (standId == null || (standId.getMostSignificantBits() == 0L && standId.getLeastSignificantBits() == 0L)) {
            return;
        }
        localAnchors.put(standId, new AnchorEntry(pos, label == null || label.isBlank() ? "Anchor" : label));
    }

    public static void receiveSignedItemList(boolean clear, UUID itemId, String label) {
        if (clear) {
            localSignedItems.clear();
        }
        if (itemId == null || (itemId.getMostSignificantBits() == 0L && itemId.getLeastSignificantBits() == 0L)) {
            return;
        }
        localSignedItems.put(itemId, new SignedItemEntry(label == null || label.isBlank() ? "Signed item" : label));
    }

    public static void receiveUnmarkCooldown(boolean clear, UUID entityUuid, String name, int remainingTicks) {
        if (clear) {
            localUnmarkCooldowns.clear();
        }
        if (entityUuid == null || (entityUuid.getMostSignificantBits() == 0L && entityUuid.getLeastSignificantBits() == 0L)) {
            return;
        }
        if (remainingTicks <= 0) {
            localUnmarkCooldowns.remove(entityUuid);
            return;
        }
        long now = MinecraftClient.getInstance().world != null ? MinecraftClient.getInstance().world.getTime() : System.currentTimeMillis() / 50L;
        localUnmarkCooldowns.put(entityUuid, new CooldownEntry(name == null || name.isBlank() ? "Cooldown" : name, now + remainingTicks));
    }

    public static void removeLocalAnchor(UUID standId) {
        localAnchors.remove(standId);
    }

    public static void removeLocalSignedItem(UUID itemId) {
        localSignedItems.remove(itemId);
    }

    public static void removeLocalUnmarkCooldown(UUID entityUuid) {
        localUnmarkCooldowns.remove(entityUuid);
    }

    public static boolean pruneLocalMarkedEntities(MinecraftClient client) {
        if (client == null || client.world == null) {
            boolean changed = !localMarkedEntities.isEmpty();
            localMarkedEntities.clear();
            localMarkedEntityNames.clear();
            missingEntitySince.clear();
            missingSyncSince.clear();
            markedSyncGenerations.clear();
            pendingMarkedSyncGeneration = -1L;
            pendingMarkedSyncTick = -1L;
            return changed;
        }
        long now = client.world.getTime();
        boolean changed = finalizePendingMarkedListSync(now);
        java.util.Iterator<Map.Entry<UUID, Long>> iterator = localMarkedEntities.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            UUID uuid = entry.getKey();
            if (entry.getValue() <= now) {
                iterator.remove();
                localMarkedEntityNames.remove(uuid);
                missingEntitySince.remove(uuid);
                missingSyncSince.remove(uuid);
                markedSyncGenerations.remove(uuid);
                changed = true;
                continue;
            }
            Entity entity = client.world.getEntity(entry.getKey());
            if (entity != null && entity.isAlive()) {
                missingEntitySince.remove(uuid);
                continue;
            }
            long missingSince = missingEntitySince.computeIfAbsent(uuid, ignored -> now);
            if (now - missingSince >= LOST_TARGET_GRACE_TICKS) {
                iterator.remove();
                localMarkedEntityNames.remove(uuid);
                missingEntitySince.remove(uuid);
                missingSyncSince.remove(uuid);
                markedSyncGenerations.remove(uuid);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean finalizePendingMarkedListSync(long now) {
        if (pendingMarkedSyncGeneration < 0L || now <= pendingMarkedSyncTick) {
            return false;
        }
        boolean changed = false;
        long syncGeneration = pendingMarkedSyncGeneration;
        long syncTick = pendingMarkedSyncTick;
        for (UUID uuid : localMarkedEntities.keySet()) {
            if (markedSyncGenerations.getOrDefault(uuid, -1L) == syncGeneration) {
                continue;
            }
            long missingSince = missingSyncSince.computeIfAbsent(uuid, ignored -> syncTick);
            long graceExpire = missingSince + LOST_TARGET_GRACE_TICKS;
            Long previousExpire = localMarkedEntities.get(uuid);
            if (previousExpire == null || previousExpire != graceExpire) {
                localMarkedEntities.put(uuid, graceExpire);
                changed = true;
            }
        }
        pendingMarkedSyncGeneration = -1L;
        pendingMarkedSyncTick = -1L;
        return changed;
    }

    /**
     * 选择要观察的目标实体，创建虚拟相机并进入观察模式。
     * 生成隐形盔甲架作为相机实体，放置在目标周围经射线检测的安全位置，
     * 然后将客户端相机绑定到该实体。
     *
     * @param entityUuid 要观察的目标实体UUID
     */
    public static void onSelectView(UUID entityUuid) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;
        currentViewingEntity = entityUuid;
        Entity target = client.world.getEntity(currentViewingEntity);
        if (target == null) return;

        cameraEntity = new ArmorStandEntity(EntityType.ARMOR_STAND, client.world);
        cameraEntity.setInvisible(true);
        cameraEntity.setInvulnerable(true);
        cameraEntity.setNoGravity(true);
        cameraEntity.setSilent(true);
        cameraEntity.setShowArms(false);
        cameraEntity.getDataTracker().set(
                ArmorStandEntity.ARMOR_STAND_FLAGS,
                (byte)(cameraEntity.getDataTracker().get(ArmorStandEntity.ARMOR_STAND_FLAGS) | ArmorStandEntity.MARKER_FLAG)
        );
        client.world.addEntity(cameraEntity);
        LOGGER.info("创建相机实体，ID = {}, 位置 = {}", cameraEntity.getUuid(), cameraEntity.getPos());

        updateCameraPosition(target);
        lookAtTarget(cameraEntity, target);
        client.cameraEntity = cameraEntity;

        if (client.player != null)
            client.player.sendMessage(Text.literal("正在注视 " + tag_pitch.entityDisplayName(target)), true);

        if (client.currentScreen instanceof Evil_eyesScreen) client.setScreen(null);
        isViewing = true;
    }

    /**
     * 退出观察模式，销毁相机实体并将客户端相机归还给玩家。
     * @param client Minecraft客户端实例
     */
    public static void exitViewMode(MinecraftClient client) {
        if (cameraEntity != null) {
            cameraEntity.discard();
            cameraEntity = null;
        }
        if (client.player != null) client.cameraEntity = client.player;
        currentViewingEntity = null;
        isViewing = false;
        uiOpenTime = 0;
        if (client.currentScreen instanceof Evil_eyesScreen) client.setScreen(null);
    }

    public static void clearMarkedEntityCache() {
        localMarkedEntities.clear();
        localMarkedEntityNames.clear();
        localAnchors.clear();
        localSignedItems.clear();
        localUnmarkCooldowns.clear();
        missingEntitySince.clear();
        missingSyncSince.clear();
        markedSyncGenerations.clear();
        pendingMarkedSyncGeneration = -1L;
        pendingMarkedSyncTick = -1L;
    }

    // 内部方法
    private static void updateCameraPosition(Entity target) {
        if (cameraEntity == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        Vec3d targetEyePos = target.getEyePos();
        Vec3d idealDir = new Vec3d(
                Math.sin(Math.toRadians(-CAM_YAW)) * Math.cos(Math.toRadians(CAM_PITCH)),
                Math.sin(Math.toRadians(CAM_PITCH)),
                Math.cos(Math.toRadians(-CAM_YAW)) * Math.cos(Math.toRadians(CAM_PITCH))
        ).normalize();
        Vec3d idealEnd = targetEyePos.add(idealDir.multiply(CAM_DISTANCE));

        RaycastContext context = new RaycastContext(
                targetEyePos, idealEnd,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                target
        );
        BlockHitResult hit = client.world.raycast(context);
        if (hit.getType() == HitResult.Type.BLOCK) {
            Vec3d hitPos = hit.getPos();
            Vec3d direction = idealDir;
            Vec3d safePos = hitPos.subtract(direction.multiply(0.5)); // 从碰撞点回退0.5格，避免穿模
            cameraEntity.setPosition(safePos);
        } else {
            cameraEntity.setPosition(idealEnd);
        }
    }

    private static void lookAtTarget(Entity entity, Entity target) {
        double dx = target.getX() - entity.getX();
        double dy = target.getEyeY() - entity.getEyeY();
        double dz = target.getZ() - entity.getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0f); // 修正Minecraft坐标系：atan2得出的角度需要减去90度
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horizontalDistance))); // 取反：atan2正值表示下方，Minecraft俯仰角正值表示上方
        entity.setYaw(yaw);
        entity.setPitch(pitch);
        entity.setHeadYaw(yaw);
    }

}
