package com.kuilunfuzhe.monvhua.features.evil_eyes;

import com.kuilunfuzhe.monvhua.config.GlobalConfigManager;
import com.kuilunfuzhe.monvhua.event.tag_pitch;
import com.kuilunfuzhe.monvhua.features.evil_eyes.server.CameraWatchManager;
import com.kuilunfuzhe.monvhua.item.evil_eyes.ClairvoyanceItem;
import com.kuilunfuzhe.monvhua.network.evil_eyes.EvilEyesPackets.*;
import com.kuilunfuzhe.monvhua.network.general_stage.GeneralStagePackets.*;
//import com.shushuwonie.client.network.clairvoyance.*;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.EulerAngle;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 千里眼核心功能模块 —— 玩家标记/盔甲架锚点/观看会话/网络包处理/锚点爆炸粒子
 * <p>
 * 负责处理千里眼(Clairvoyance)物品的所有服务端逻辑：
 * <ul>
 *   <li>实体标记：手动标记、自动标记（实体注视检测）、过期/超出范围清理</li>
 *   <li>盔甲架锚点：放置、存活时间扫描、视线方向检测标记、超时爆炸销毁</li>
 *   <li>观看会话：启动、每日次数消耗判定、超时强制退出</li>
 *   <li>全局配置：接收客户端配置请求、OP玩家配置更新与广播</li>
 * </ul>
 */
public class Evil_Eyes {
    /** 每个玩家的绝对标记上限 */
    public static final int MAX_MARKED_ENTITIES = 5;
    public static Item CLAIRVOYANCE_ITEM;

    private static final double ANCHOR_RANGE = 30.0;
    private static final double ANCHOR_RANGE_SQ = ANCHOR_RANGE * ANCHOR_RANGE;
    private static final int TICKS_PER_SECOND = 20;
    private static final int MARK_EXPIRE_TICKS = 400;          // 20 秒
    private static final int ANCHOR_TIMEOUT_TICKS = 3600;      // 180 秒
    private static final int INITIAL_MARK_TICKS = 40;           // 2 秒
    private static final int COOLDOWN_TICKS = 100;              // 5 秒
    private static final double PARTICLE_BROADCAST_RANGE_SQ = 64.0 * 64.0;

    /** 玩家标记列表：玩家UUID → (目标UUID → 过期tick) */
    private static final Map<UUID, Map<UUID, Long>> playerMarkedEntities = new ConcurrentHashMap<>();
    /**
     * 手动取消标记冷却表：被取消的实体在冷却期内不会被锚点/自动标记重新加回。
     * key=实体UUID, value=冷却结束tick（当前tick+100，即5秒）
     */
    private static final Map<UUID, Long> recentlyUnmarked = new ConcurrentHashMap<>();
    /**
     * 超出范围追踪表：当标记实体离开标记玩家的30格范围时记录首次检测tick，
     * 持续超出20tick（1秒）后自动移除标记。key=玩家UUID → (实体UUID → 首次超出tick)
     */
    private static final Map<UUID, Map<UUID, Long>> outOfRangeSince = new ConcurrentHashMap<>();
    private static final Logger LOGGER = LoggerFactory.getLogger("Clairvoyance");



    /**
     * 观看会话内部数据记录
     * <ul>
     *   <li>{@code targetUuid} —— 被观看实体的UUID</li>
     *   <li>{@code startTick} —— 会话开始的游戏刻</li>
     *   <li>{@code counted} —— 是否已扣除每日次数（防止同一会话重复扣减）</li>
     * </ul>
     */
    private static class WatchingInfo {
        UUID targetUuid;
        long startTick;
        boolean counted;
        WatchingInfo(UUID targetUuid, long startTick) {
            this.targetUuid = targetUuid;
            this.startTick = startTick;
            this.counted = false;
        }
    }
    /** 活跃观看会话：观看者UUID → WatchingInfo */
    private static final Map<UUID, WatchingInfo> watchingPlayers = new ConcurrentHashMap<>();

    /** 盔甲架锚点归属：盔甲架UUID → 主人UUID */
    public static final Map<UUID, UUID> armorStandOwner = new ConcurrentHashMap<>();
    /** 盔甲架锚点生成时刻：盔甲架UUID → 生成时的游戏刻（用于180秒超时判定） */
    public static final Map<UUID, Long> armorStandSpawnTick = new ConcurrentHashMap<>();

    /** 粒子效果发送计数器，每tick自增，每10tick（0.5秒）触发一次锚点粒子广播 */
    private static int particleTickCounter = 0;

    /** 全局配置管理器引用，由 {@link #initialize} 设置 */
    public static GlobalConfigManager configManager;

//    static {
//        Identifier id = Identifier.of("monvhua", "clairvoyance_item");
//        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, id);
//        Item.Settings settings = new Item.Settings()
//                .registryKey(key)
//                .translationKey("item.clairvoyance.clairvoyance_item")
//                .maxCount(1);
//        CLAIRVOYANCE_ITEM = new ClairvoyanceItem(settings);
//    }
//
//    static {
//        CLAIRVOYANCE_ITEM = new ClairvoyanceItem(new Item.Settings().maxCount(1));
//    }


    // ========== 辅助方法 ==========
    /** 清空信号UUID：发送全零UUID到客户端表示"清除标记列表"，作为批量更新的前置操作 */
    private static final UUID CLEAR_SIGNAL = new UUID(0, 0);

    private static void sendMarkedListToClient(ServerPlayerEntity player, Map<UUID, Long> marks, int maxDisplayCount) {
        ServerPlayNetworking.send(player, new EntityMarkedS2C(CLEAR_SIGNAL, ""));
        for (Map.Entry<UUID, Long> entry : marks.entrySet()) {
            UUID uuid = entry.getKey();
            Entity e = player.getWorld().getEntity(uuid);
            // 过滤锚点盔甲架
            if (isAnchorStand(e)) continue;
            String name = e == null ? "未知实体" : e.getName().getString();
            ServerPlayNetworking.send(player, new EntityMarkedS2C(uuid, name, tag_pitch.tagForEntity(e)));
        }
    }

    private static int getCurrentMarkCount(UUID playerUuid) {
        Map<UUID, Long> marks = playerMarkedEntities.get(playerUuid);
        int markCount = marks == null ? 0 : marks.size();
        int activeAnchors = configManager != null ? configManager.getActiveParrotCount(playerUuid) : 0;
        return markCount + activeAnchors;
    }

    private static boolean isAnchorStand(Entity e) {
        if (!(e instanceof ArmorStandEntity as)) return false;
        Text name = as.getCustomName();
        return name != null && "clairvoyance_evil_eyes".equals(name.getString());
    }

    /**
     * 根据玩家的记分板分数计算当前千里眼阶段
     *
     * @param player 目标玩家
     * @param mgr    全局配置管理器，用于将原始分数映射为阶段值
     * @return 阶段编号（1~N），记分板不存在或无分数时返回1
     */
    public static int getPlayerStage(ServerPlayerEntity player, GlobalConfigManager mgr) {
        Scoreboard scoreboard = player.getScoreboard();
        ScoreboardObjective objective = scoreboard.getNullableObjective("monvhua");
        if (objective == null) return 1;
        var score = scoreboard.getScore(player, objective);
        if (score == null) return 1;
        int rawScore = Math.clamp(score.getScore(), 0, 100);
        return mgr.getStageByScore(rawScore);
    }

    // 保留视线检测方法（虽然后面不用，但其他功能可能调用）
    private static boolean hasLineOfSight(Entity entity, Entity target) {
        World world = entity.getWorld();
        Vec3d start = entity.getEyePos();
        Vec3d end = target.getEyePos();
        RaycastContext context = new RaycastContext(start, end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                entity);
        HitResult result = world.raycast(context);
        return result.getType() == HitResult.Type.MISS ||
                (result.getType() == HitResult.Type.ENTITY && ((EntityHitResult) result).getEntity() == target);
    }

    /**
     * 向锚点附近的玩家发送爆炸粒子包（锚点被摧毁时的视觉反馈）
     *
     * @param stand  被销毁的盔甲架锚点
     * @param server 服务端实例
     */
    public static void sendExplosionToNearbyPlayers(Entity stand, MinecraftServer server) {
        if (stand == null) return;
        Vec3d pos = stand.getPos();
        // 向半径64格（64^2=4096）内的所有玩家发送爆炸包
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.squaredDistanceTo(pos) < PARTICLE_BROADCAST_RANGE_SQ) {
                ServerPlayNetworking.send(player, new ExplosionParticleS2C(pos));
            }
        }
    }

    /** 玩家锚点位置信息：玩家UUID → 锚点位置与最后观测时间 */
    public static final Map<UUID, AnchorInfo> anchors = new ConcurrentHashMap<>();

    /** 锚点位置信息记录（用于玩家放置的盔甲架锚点追踪） */
    public static class AnchorInfo {
        /** 锚点世界坐标 */
        public Vec3d pos;
        /** 最后一次被扫描到的时间戳 */
        public long lastSeenTime;
    }

    /**
     * 判断玩家是否处于观看模式（通过 watchingPlayers 表判定）
     *
     * @param player 目标玩家
     * @return 是否正在观看某个实体
     */
    public static boolean isWatching(ServerPlayerEntity player) {
        return watchingPlayers.containsKey(player.getUuid());
    }

    /**
     * 强制退出观看模式 —— 移除会话记录并发送退出包。
     * 用于超时、受伤、断线等场景的强制退出。
     *
     * @param player 目标玩家
     * @param server 服务端实例
     */
    public static void forceStopWatching(ServerPlayerEntity player, MinecraftServer server) {
        watchingPlayers.remove(player.getUuid());
        CameraWatchManager.stopWatching(player, server);
        ServerPlayNetworking.send(player, new ForceExitViewS2C());
    }

    /**
     * 启动一个带超时追踪的观看会话（供 /watch 命令等调用）
     * 添加到 watchingPlayers 使得 tick 处理器能进行超时强制退出和次数消耗
     */
    public static void startWatchSession(ServerPlayerEntity player, UUID targetUuid, long currentTick) {
        watchingPlayers.remove(player.getUuid());
        watchingPlayers.put(player.getUuid(), new WatchingInfo(targetUuid, currentTick));
        CameraWatchManager.startWatching(player, targetUuid, player.getServer());
    }

    /**
     * 清除指定玩家的标记列表（用于断线清理）
     */
    public static void clearPlayerMarks(UUID playerUuid) {
        playerMarkedEntities.remove(playerUuid);
    }

    /**
     * 程序化添加标记（供 signed_evil 等功能使用）
     */
    public static void addMark(UUID markerUuid, UUID targetUuid, long expiryTick) {
        playerMarkedEntities.computeIfAbsent(markerUuid, k -> new ConcurrentHashMap<>())
            .put(targetUuid, expiryTick);
    }

    /**
     * 检查标记是否活跃
     */
    public static boolean hasActiveMark(UUID markerUuid, UUID targetUuid) {
        Map<UUID, Long> marks = playerMarkedEntities.get(markerUuid);
        return marks != null && marks.containsKey(targetUuid);
    }


    /**
     * 同步标记列表到客户端（供 signed_evil 等功能使用）
     */
    public static void syncMarkedListToClient(ServerPlayerEntity marker) {
        Map<UUID, Long> marks = playerMarkedEntities.get(marker.getUuid());
        if (marks == null) return;
        int stage = getPlayerStage(marker, configManager);
        int maxMarks = configManager.getStageConfig(stage).maxMarks();
        sendMarkedListToClient(marker, marks, maxMarks);
    }

    /**
     * 清除指定玩家的所有锚点盔甲架，并释放对应配额。
     *
     * @param playerUuid 玩家UUID
     * @param server     服务端实例
     * @return 清除的锚点数量
     */
    public static int clearAnchorsForPlayer(UUID playerUuid, MinecraftServer server) {
        int count = 0;
        World world = server.getWorld(World.OVERWORLD);
        if (world == null) return 0;
        for (Map.Entry<UUID, UUID> entry : armorStandOwner.entrySet().stream().toList()) {
            if (entry.getValue().equals(playerUuid)) {
                UUID standId = entry.getKey();
                Entity stand = world.getEntity(standId);
                if (stand != null) {
                    stand.remove(Entity.RemovalReason.DISCARDED);
                }
                armorStandOwner.remove(standId);
                armorStandSpawnTick.remove(standId);
                count++;
            }
        }
        if (count > 0) {
            for (int i = 0; i < count; i++) {
                configManager.removeActiveParrot(playerUuid);
            }
        }
        return count;
    }

    // ========== 初始化 ==========
    /**
     * 千里眼核心系统的初始化入口。
     * <p>
     * 依次完成以下注册：
     * <ol>
     *   <li>物品注册（ClairvoyanceItem）并添加到创造模式工具物品组</li>
     *   <li>全局配置的客户端请求与OP更新广播（RequestGlobalConfig / UpdateGlobalConfig）</li>
     *   <li>手动标记/取消标记（MarkEntity / UnmarkEntity）</li>
     *   <li>观看时长计时与每日次数消耗（ServerTick）</li>
     *   <li>退出观看、选择观看实体（ExitView / SelectView）</li>
     *   <li>受伤强制退出（ALLOW_DAMAGE）</li>
     *   <li>过期/死亡/超出范围标记清理（ServerTick）</li>
     *   <li>自动标记：实体注视手持千里眼的玩家（ServerTick，每5tick）</li>
     *   <li>盔甲架锚点放置（PlaceParrot）</li>
     *   <li>锚点粒子广播 + 实体扫描标记 + 超时销毁（ServerTick）</li>
     *   <li>玩家阶段同步（ServerTick，每秒）</li>
     *   <li>锚点死亡清理（AFTER_DEATH）</li>
     * </ol>
     *
     * @param configManager 全局配置管理器
     */
    public static void initialize(GlobalConfigManager configManager) {
        Evil_Eyes.configManager = configManager;


//        SelectView.register();      // 客户端到服务端
//        MarkEntityC2S.register();      // 客户端到服务端
//        ExitViewC2S.register();        // 客户端到服务端
//        ForceExitViewS2C.register();   // 客户端到服务端

        Identifier id = Identifier.of("monvhua", "clairvoyance");
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, id);
        Item.Settings settings = new Item.Settings()
                .registryKey(key)
                .maxCount(1);
        CLAIRVOYANCE_ITEM = new ClairvoyanceItem(settings);


        Registry.register(Registries.ITEM, id, CLAIRVOYANCE_ITEM);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(CLAIRVOYANCE_ITEM));



        // 处理客户端请求全局配置
        ServerPlayNetworking.registerGlobalReceiver(RequestGlobalConfigC2S.ID, (packet, context) -> {
            ServerPlayerEntity player = context.player();
            String configJson = configManager.getConfigJson(); // 你需要实现这个方法，将当前配置序列化为 JSON
            ServerPlayNetworking.send(player, new GlobalConfigS2C(configJson));
        });

// 处理客户端更新配置（仅允许 OP 或创造模式玩家执行）
        ServerPlayNetworking.registerGlobalReceiver(UpdateGlobalConfigC2S.ID, (packet, context) -> {
            ServerPlayerEntity player = context.player();
            // 权限检查：建议只允许服主或拥有特定权限的玩家修改
            if (!player.hasPermissionLevel(2)) {
                player.sendMessage(Text.literal("§c你没有权限修改全局配置"), true);
                return;
            }
            // 更新配置管理器中的数据
            configManager.updateConfig(
                    packet.stage(),
                    packet.dailyLimit(),
                    packet.maxMarks(),
                    packet.minScore(),
                    packet.maxScore(),
                    packet.parrotDailyLimit(),
                    packet.uiDrainRate(),
                    packet.watchDrainRate(),
                    packet.regenRate()
            );
            // 保存配置到文件（如果需要持久化）
            configManager.save();
            // 广播给所有在线玩家
            String newConfigJson = configManager.getConfigJson();
            GlobalConfigS2C configPacket = new GlobalConfigS2C(newConfigJson);
            for (ServerPlayerEntity onlinePlayer : player.getServer().getPlayerManager().getPlayerList()) {
                ServerPlayNetworking.send(onlinePlayer, configPacket);
            }
            player.sendMessage(Text.literal("§a全局配置已更新"), true);
        });



        // 1. 手动标记/取消标记（不消耗每日次数）
        ServerPlayNetworking.registerGlobalReceiver(ClairvoyanceUiStateC2S.ID, (payload, context) -> {
            CameraWatchManager.setUiState(context.player(), payload.open(), payload.previewCount(), payload.expanded());
        });

        ServerPlayNetworking.registerGlobalReceiver(MarkEntityC2S.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            World world = player.getWorld();
            Entity target = world.getEntityById(payload.entityId());
            if (!(target instanceof LivingEntity) || player.getMainHandStack().getItem() != CLAIRVOYANCE_ITEM) return;

            int stage = getPlayerStage(player, configManager);
            int maxMarks = configManager.getStageConfig(stage).maxMarks();
            if (getCurrentMarkCount(player.getUuid()) >= maxMarks) {
                player.sendMessage(Text.literal("§c已达当前阶段最大标记数量 (" + maxMarks + ")"), true);
                return;
            }

            Map<UUID, Long> marks = playerMarkedEntities.computeIfAbsent(player.getUuid(), k -> new ConcurrentHashMap<>());
            UUID targetUuid = target.getUuid();
            long now = world.getTime();

            if (marks.containsKey(targetUuid)) {
                marks.remove(targetUuid);
                recentlyUnmarked.put(targetUuid, world.getTime() + COOLDOWN_TICKS); // 5秒冷却
                player.sendMessage(Text.literal("§e已取消标记 " + tag_pitch.entityDisplayName(target)), true);
                sendMarkedListToClient(player, marks, maxMarks);
            } else {
                if (marks.size() >= MAX_MARKED_ENTITIES) {
                    player.sendMessage(Text.literal("§c标记已达绝对上限 (" + MAX_MARKED_ENTITIES + " 个)"), true);
                    return;
                }
                long expire = now + INITIAL_MARK_TICKS; // 2秒
                marks.put(targetUuid, expire);
                player.sendMessage(Text.literal("§a你感觉 " + tag_pitch.entityDisplayName(target) + " 在§6注视§r你"), true);
                sendMarkedListToClient(player, marks, maxMarks);
            }
        });

// 1.5 手动取消标记（从GUI点击删除）
        ServerPlayNetworking.registerGlobalReceiver(UnmarkEntityC2S.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            UUID targetUuid = payload.entityUuid();
            Map<UUID, Long> marks = playerMarkedEntities.get(player.getUuid());
            if (marks == null || !marks.containsKey(targetUuid)) return;
            marks.remove(targetUuid);
            recentlyUnmarked.put(targetUuid, player.getWorld().getTime() + COOLDOWN_TICKS);
            int stage = getPlayerStage(player, configManager);
            int maxMarks = configManager.getStageConfig(stage).maxMarks();
            sendMarkedListToClient(player, marks, maxMarks);
            player.sendMessage(Text.literal("§e已移除标记"), true);
        });

// 2. 观看计时消耗每日次数 + 超时强制退出
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long now = server.getWorld(World.OVERWORLD).getTime();
            // 遍历副本避免并发修改
            for (Map.Entry<UUID, WatchingInfo> entry : watchingPlayers.entrySet().stream().toList()) {
                UUID playerUuid = entry.getKey();
                WatchingInfo info = entry.getValue();
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerUuid);
                if (player == null) continue;

                int stage = getPlayerStage(player, configManager);
                int requiredTicks = configManager.getStageConfig(stage).watchRequiredTicks();
                long elapsed = now - info.startTick;

                if (Boolean.getBoolean("monvhua.clairvoyance.legacyTimeout") && elapsed >= requiredTicks) {
                    if (!info.counted) {
                        // 第一次达到时长，扣除每日次数
                        player.sendMessage(Text.literal("§e魔力流失，观看消耗次数"), true);
                        info.counted = true;
                    }
                    // 无论是否已扣除，只要达到时长就强制退出观看
                    CameraWatchManager.stopWatching(player, server);
                    ServerPlayNetworking.send(player, new ForceExitViewS2C());
                    watchingPlayers.remove(playerUuid);
                    player.sendMessage(Text.literal("§8头昏脑胀，看来只能看到这了"), true);
                }
            }
        });

        // 3. 退出观看
        ServerPlayNetworking.registerGlobalReceiver(ExitViewC2S.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            playerMarkedEntities.remove(player.getUuid());
            CameraWatchManager.stopWatching(player, player.getServer());
            watchingPlayers.remove(player.getUuid());
            ServerPlayNetworking.send(player, new ForceExitViewS2C());
            player.sendMessage(Text.literal("§e已退出观看"), true);
        });

        // 4. 选择观看实体（需在锚点30格范围内）
        ServerPlayNetworking.registerGlobalReceiver(SelectView.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            UUID selected = payload.entityUuid();
            Map<UUID, Long> marks = playerMarkedEntities.get(player.getUuid());
            if (marks == null || !marks.containsKey(selected)) return;
            Long expire = marks.get(selected);
            if (expire == null || expire <= player.getWorld().getTime()) return;

            // 检查被观看实体是否在锚点30格范围内或自身30格范围内（自动标记同理）
            Entity targetEntity = player.getWorld().getEntity(selected);
            if (targetEntity == null) return;
            boolean nearAnchor = targetEntity.squaredDistanceTo(player) < ANCHOR_RANGE_SQ;
            if (!nearAnchor) {
                for (Map.Entry<UUID, UUID> entry : armorStandOwner.entrySet()) {
                    if (!entry.getValue().equals(player.getUuid())) continue;
                    Entity anchor = player.getWorld().getEntity(entry.getKey());
                    if (anchor != null && anchor.isAlive() && targetEntity.squaredDistanceTo(anchor) < ANCHOR_RANGE_SQ) {
                        nearAnchor = true;
                        break;
                    }
                }
            }
            if (!nearAnchor) {
                player.sendMessage(Text.literal("§c目标不在任何锚点30格范围内"), true);
                return;
            }
            watchingPlayers.remove(player.getUuid());
            watchingPlayers.put(player.getUuid(), new WatchingInfo(selected, player.getWorld().getTime()));

            // 根据玩家偏好选择观看系统
            String mode = com.kuilunfuzhe.monvhua.MonvhuaMod.VIEW_MODE_PREFERENCE.getOrDefault(player.getUuid(), "viewport");
            if ("legacy".equals(mode)) {
                // 旧系统：通知客户端使用本地盔甲架相机
                ServerPlayNetworking.send(player, new SelectView(selected));
            } else {
                // 新系统（默认）：服务端 CameraWatch
                CameraWatchManager.startWatching(player, selected, player.getServer());
            }
        });

        // 5. 受伤退出
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity player) {
                if (playerMarkedEntities.containsKey(player.getUuid())) {
                    playerMarkedEntities.remove(player.getUuid());
                    CameraWatchManager.stopWatching(player, player.getServer());
                    ServerPlayNetworking.send(player, new ForceExitViewS2C());
                    player.sendMessage(Text.literal("§c受到攻击，观看中断"), true);
                }
            }
            return true;
        });

        // 6. 清理过期标记、死亡实体和超出范围实体
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long now = server.getWorld(World.OVERWORLD).getTime();
            boolean periodicSync = server.getTicks() % 40 == 0;
            for (Map.Entry<UUID, Map<UUID, Long>> playerEntry : playerMarkedEntities.entrySet()) {
                UUID playerUuid = playerEntry.getKey();
                Map<UUID, Long> marks = playerEntry.getValue();
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerUuid);
                boolean changed = false;
                // 获取该玩家超出范围的追踪表
                Map<UUID, Long> outRange = outOfRangeSince.computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>());
                Iterator<Map.Entry<UUID, Long>> it = marks.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<UUID, Long> entry = it.next();
                    UUID entityUuid = entry.getKey();
                    long expire = entry.getValue();
                    Entity entity = server.getWorld(World.OVERWORLD).getEntity(entityUuid);
                    boolean dead = (entity == null || !entity.isAlive());
                    if (expire <= now || dead) {
                        it.remove();
                        changed = true;
                        outRange.remove(entityUuid);
                        continue;
                    }
                    // 超出范围检测（仅当玩家在线时）
                    if (player != null && entity != null) {
                        boolean inRange = entity.squaredDistanceTo(player) < ANCHOR_RANGE_SQ;
                        // 检查是否在锚点30格内
                        if (!inRange) {
                            for (Map.Entry<UUID, UUID> ae : armorStandOwner.entrySet()) {
                                if (!ae.getValue().equals(playerUuid)) continue;
                                Entity anchor = server.getWorld(World.OVERWORLD).getEntity(ae.getKey());
                                if (anchor != null && anchor.isAlive() && entity.squaredDistanceTo(anchor) < ANCHOR_RANGE_SQ) {
                                    inRange = true;
                                    break;
                                }
                            }
                        }
                        if (inRange) {
                            outRange.remove(entityUuid);
                        } else {
                            Long since = outRange.get(entityUuid);
                            if (since == null) {
                                outRange.put(entityUuid, now);
                            } else if (now - since >= TICKS_PER_SECOND) {
                                it.remove();
                                changed = true;
                                outRange.remove(entityUuid);
                            }
                        }
                    }
                }
                // 清理该玩家空的追踪表
                if (outRange.isEmpty()) {
                    outOfRangeSince.remove(playerUuid);
                }
                if ((changed || periodicSync) && player != null) {
                    int stage = getPlayerStage(player, configManager);
                    int maxMarks = configManager.getStageConfig(stage).maxMarks();
                    sendMarkedListToClient(player, marks, maxMarks);
                }
            }
            playerMarkedEntities.entrySet().removeIf(entry -> entry.getValue().isEmpty());
            recentlyUnmarked.entrySet().removeIf(e -> e.getValue() <= now);
        });

        // 7. 自动标记：实体看向手持千里眼的玩家（注视检测）
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 5 != 0) return; // 每5tick（0.25秒）执行一次，降低开销
            World world = server.getWorld(World.OVERWORLD);
            if (world == null) return;
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (player.getMainHandStack().getItem() != CLAIRVOYANCE_ITEM &&
                        player.getOffHandStack().getItem() != CLAIRVOYANCE_ITEM) continue;
                int stage = getPlayerStage(player, configManager);
                int maxMarks = configManager.getStageConfig(stage).maxMarks();
                Map<UUID, Long> marks = playerMarkedEntities.computeIfAbsent(player.getUuid(), k -> new ConcurrentHashMap<>());
                double range = ANCHOR_RANGE;
                Box searchBox = player.getBoundingBox().expand(range);
                for (Entity entity : world.getOtherEntities(player, searchBox, e ->
                        e instanceof LivingEntity && e.isAlive() && !isAnchorStand(e))) {
                    if (marks.containsKey(entity.getUuid())) continue;
                    if (recentlyUnmarked.getOrDefault(entity.getUuid(), 0L) > world.getTime()) continue;
                    if (getCurrentMarkCount(player.getUuid()) >= maxMarks) continue;
                    if (!hasLineOfSight(entity, player)) continue;
                    Vec3d toPlayer = player.getPos().subtract(entity.getPos()).normalize();
                    Vec3d entityLook = entity.getRotationVec(1.0f);
                    // 点积>0.5表示实体视线与玩家方向夹角<60°，即实体大致在看向玩家
                    if (entityLook.dotProduct(toPlayer) < 0.5) continue;
                    long now = world.getTime();
                    marks.put(entity.getUuid(), now + MARK_EXPIRE_TICKS); // 自动标记有效期20秒（400tick）
                    sendMarkedListToClient(player, marks, maxMarks);
                    player.sendMessage(Text.literal(tag_pitch.entityDisplayName(entity) + "在注视着你"), true);
                }
            }
        });

        // ==================== 盔甲架锚点功能 ====================
        ServerPlayNetworking.registerGlobalReceiver(PlaceParrotC2S.ID, (packet, context) -> {
            ServerPlayerEntity player = context.player();
            World world = player.getWorld();
            Vec3d pos = packet.pos();

//            LOGGER.info("收到放置盔甲架请求，玩家：{}，位置：{}", player.getName().getString(), pos);
            BlockPos blockPos = BlockPos.ofFloored(pos);
            if (!world.getBlockState(blockPos).isAir() && !world.getBlockState(blockPos).isReplaceable()) {
                player.sendMessage(Text.literal("§c位置不可用"), true);
                return;
            }

            int stage = getPlayerStage(player, configManager);
            int maxMarks = configManager.getStageConfig(stage).maxMarks();
            if (getCurrentMarkCount(player.getUuid()) >= maxMarks) {
                player.sendMessage(Text.literal("§c已达当前阶段最大总标记数 (" + maxMarks + ")"), true);
                return;
            }
            if (!configManager.canPlaceParrot(player.getUuid(), stage)) {
                player.sendMessage(Text.literal("§c今日放置锚点次数已达上限"), true);
//                LOGGER.warn("放置被拒绝：次数或活跃数达上限");
                return;
            }

            ArmorStandEntity armorStand = new ArmorStandEntity(EntityType.ARMOR_STAND, world);
            armorStand.setPosition(pos.x, pos.y, pos.z);
            float angle = 90.0F; // 角度制，需转弧度
            EulerAngle rotation = new EulerAngle((float)Math.toRadians(angle), 0.0F, 0.0F);
// 先设置所有属性（包括反射设置小体型）
            armorStand.getAttributeInstance(EntityAttributes.SCALE).setBaseValue(0.3);   // 缩小到30%
            armorStand.setHideBasePlate(true);
            armorStand.setShowArms(false);
            armorStand.setNoGravity(true);
            armorStand.setSilent(true);
            armorStand.setInvulnerable(false);
            armorStand.setInvisible(false);
            armorStand.setCustomName(Text.literal("clairvoyance_evil_eyes"));
            armorStand.setCustomNameVisible(false);
            armorStand.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(1.0);
            armorStand.setHealth(1.0f);
//            armorStand.setMarker(true);
// 反射设置小体型
            try {
                Method setSmall = ArmorStandEntity.class.getDeclaredMethod("setSmall", boolean.class);
                setSmall.setAccessible(true);
                setSmall.invoke(armorStand, true);
            } catch (Exception e) {
//                LOGGER.warn("无法设置小体型", e);
            }
// 只生成一次
            if (!world.spawnEntity(armorStand)) {
//                LOGGER.error("盔甲架生成失败！");
                player.sendMessage(Text.literal("§c锚点生成失败"), true);
                return;
            }
// 生成成功后记录
            UUID standUuid = armorStand.getUuid();
            UUID ownerUuid = player.getUuid();
            armorStandOwner.put(standUuid, ownerUuid);
            armorStandSpawnTick.put(standUuid, world.getTime());
            configManager.recordPlaceParrot(ownerUuid, stage);
            player.sendMessage(Text.literal("§a锚点已放置"), true);
//            LOGGER.info("盔甲架 {} 已生成，存活: {}", standUuid, armorStand.isAlive());



        });

        // ==================== 盔甲架锚点扫描逻辑（增加视线检测） ====================
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            World world = server.getWorld(World.OVERWORLD);
            if (world == null) return;
            // 在扫描循环中，对每个活着的盔甲架：
            particleTickCounter++;
            if (particleTickCounter % 10 == 0) { // 每 10 tick 发送一次（0.5秒）
                for (UUID standId : armorStandOwner.keySet()) {
                    Entity stand = world.getEntity(standId);
                    if (stand == null || !stand.isAlive()) continue;
                    // 计算胸口位置：盔甲架位置 + 向上 0.6 格（因为小体型时胸口较低，可调整）
                    Vec3d chestPos = stand.getPos().add(0, 0.6, 0);
                    // 发送给附近玩家（以盔甲架为中心半径 64 格内）
                    // 广播给所有附近玩家
                    for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                        if (player.squaredDistanceTo(stand) < PARTICLE_BROADCAST_RANGE_SQ) { // 64格半径
                            ServerPlayNetworking.send(player, new AnchorParticleS2C(standId, chestPos, 0));
                            ServerPlayNetworking.send(player, new AnchorParticleS2C(standId, chestPos, 1));
                        }
                    }
                    // 也可广播给所有玩家，根据需要
                }
            }


            for (UUID standId : armorStandOwner.keySet().stream().toList()) {
                Entity stand = world.getEntity(standId);
                if (stand == null || !stand.isAlive()) {
                    if (stand != null) sendExplosionToNearbyPlayers(stand, server);
                    UUID owner = armorStandOwner.remove(standId);
                    if (owner != null) configManager.removeActiveParrot(owner);
                    armorStandSpawnTick.remove(standId);
                    continue;
                }

                // 在遍历 armorStandOwner 的循环中，检查超时
                Long spawnTick = armorStandSpawnTick.get(standId);
                if (spawnTick != null && world.getTime() - spawnTick >= ANCHOR_TIMEOUT_TICKS) { // 3600tick=180秒=3分钟，到期销毁锚点
                    sendExplosionToNearbyPlayers(stand, server);

                    stand.remove(Entity.RemovalReason.DISCARDED);   // 销毁盔甲架
                    UUID owner = armorStandOwner.remove(standId);
                    if (owner != null) configManager.removeActiveParrot(owner);
                    armorStandSpawnTick.remove(standId);
                    // 可选：通知玩家
                    ServerPlayerEntity player = server.getPlayerManager().getPlayer(owner);
                    if (player != null) player.sendMessage(Text.literal("§c锚点已失去联系"), true);
                    continue; // 跳过后续扫描
                }

                UUID ownerId = armorStandOwner.get(standId);
                if (ownerId == null) continue;
                ServerPlayerEntity owner = server.getPlayerManager().getPlayer(ownerId);
                if (owner == null) continue;

                Map<UUID, Long> marks = playerMarkedEntities.computeIfAbsent(ownerId, k -> new ConcurrentHashMap<>());
                int stage = getPlayerStage(owner, configManager);
                int maxMarks = configManager.getStageConfig(stage).maxMarks();
                if (getCurrentMarkCount(ownerId) >= maxMarks) continue;

                double range = ANCHOR_RANGE;
                Box searchBox = stand.getBoundingBox().expand(range);
                List<LivingEntity> nearby = world.getEntitiesByClass(LivingEntity.class, searchBox,
                        e -> e.isAlive() && e != owner && e != stand && !isAnchorStand(e));

                for (LivingEntity living : nearby) {
                    if (marks.containsKey(living.getUuid())) continue;
                    if (recentlyUnmarked.getOrDefault(living.getUuid(), 0L) > world.getTime()) continue;
                    if (getCurrentMarkCount(ownerId) >= maxMarks) break;

                    // 1. 视线检测：从实体眼睛到盔甲架是否有遮挡
                    if (!hasLineOfSight(living, stand)) continue;

                    // 2. 方向检测：实体是否面向盔甲架（点积 > 0.5 表示大致看向目标）
                    Vec3d toStand = stand.getPos().subtract(living.getEyePos()).normalize();
                    Vec3d lookVec = living.getRotationVec(1.0f);
                    if (lookVec.dotProduct(toStand) < 0.5) continue;

                    // 通过检测，标记该实体
                    long now = world.getTime();
                    long expire = now + MARK_EXPIRE_TICKS;  // 20秒
                    marks.put(living.getUuid(), expire);
                    sendMarkedListToClient(owner, marks, maxMarks);
                    owner.sendMessage(Text.literal("§a[锚点] " + tag_pitch.entityDisplayName(living) + " 在注视着你"), true);
                }
            }
        });

        // 在 Evil_Eyes.initialize 末尾添加
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % TICKS_PER_SECOND == 0) { // 每秒同步一次阶段
                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    int stage = getPlayerStage(player, configManager);
                    ServerPlayNetworking.send(player, new PlayerStageS2C(stage));
                }
            }
        });

        // 全局锚点死亡处理器（替代之前的逐个注册方式，避免监听器泄漏）
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof ArmorStandEntity)) return;
            UUID standId = entity.getUuid();
            if (!armorStandOwner.containsKey(standId)) return;
            UUID ownerId = armorStandOwner.remove(standId);
            armorStandSpawnTick.remove(standId);
            if (ownerId != null) {
                configManager.removeActiveParrot(ownerId);
            }
            MinecraftServer server = entity.getServer();
            if (server != null) sendExplosionToNearbyPlayers(entity, server);
        });
    }

}
