package com.kuilunfuzhe.monvhua.item.through;

import com.kuilunfuzhe.monvhua.config.GlobalConfigManager;
import com.kuilunfuzhe.monvhua.item.config.ThroughConfig;
import com.kuilunfuzhe.monvhua.network.through.ThroughStateS2CPacket;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.consume.UseAction;
import net.minecraft.network.packet.s2c.play.StopSoundS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 虚化魔法，右键长按进入隐秘（隐身）状态。
 * 核心机制：使用后先进入准备阶段（施加失明/黑暗效果），
 * 延迟一定时间后获得隐身效果；松开右键或条件不满足时退出隐秘。
 * 特色：根据玩家阶段的速度修正、心跳音效、客户端状态同步。
 */
public class ThroughItem extends Item {
    /** 隐秘物品的注册ID */
    public static final Identifier ID = Identifier.of("monvhua", "through");
    /** 心跳音效的注册ID，隐秘状态下周期性播放，模拟紧张氛围 */
    public static final Identifier HEART_SOUND_ID = Identifier.of("monvhua", "heart");
    /** 隐秘速度修正器的ID，用于给隐秘状态的玩家施加移动速度倍率 */
    public static final Identifier SPEED_MODIFIER_ID = Identifier.of("monvhua", "secrecy_speed_multiplier");
    /** 隐秘物品单例，在initialize()中赋值 */
    public static ThroughItem THROUGH_ITEM;
    /** 心跳音效事件，在initialize()中注册 */
    public static SoundEvent HEART_SOUND;

    /** -1表示无限时长药水效果 */
    private static final int INFINITE_DURATION = -1;
    /** 心跳音效播放间隔（tick），40 tick ≈ 2秒 */
    private static final int HEART_SOUND_INTERVAL_TICKS = 40;
    /** 心跳音效最大音量 */
    private static final float HEART_SOUND_MAX_VOLUME = 1.2F;
    /** 沉默标签，有此标签的玩家无法使用隐秘物品 */
    private static final String SILENCED_TAG = "Silenced";
    /** 等待消失的玩家映射：UUID → 世界时间（tick），到达时间后施加隐身效果 */
    private static final Map<UUID, Long> VANISH_PENDING_TICKS = new ConcurrentHashMap<>();
    /** 心跳音效倒计时：UUID → 剩余tick数，为0时播放音效并重置 */
    private static final Map<UUID, Integer> HEART_SOUND_TICKS = new ConcurrentHashMap<>();
    /** 当前处于隐秘状态的玩家集合 */
    private static final Set<UUID> ACTIVE_SECRECY = ConcurrentHashMap.newKeySet();
    /** 正在退出隐秘状态的玩家集合，用于tick循环中跳过处理 */
    private static final Set<UUID> EXITING_SECRECY = ConcurrentHashMap.newKeySet();
    /** 已完成延迟并进入隐身，可尝试穿墙的玩家 */
    private static final Set<UUID> PHASE_READY = ConcurrentHashMap.newKeySet();
    /** 通过脚部射线检测后，短暂开启 noClip 以尝试进入墙体的玩家 */
    private static final Set<UUID> PHASE_ATTEMPTING = ConcurrentHashMap.newKeySet();
    /** 已经进入墙体内部的穿墙锁定玩家 */
    private static final Set<UUID> PHASE_LOCKED = ConcurrentHashMap.newKeySet();
    /** 松开使用键或被沉默后滞留在墙体内的玩家，保留锁视角/无重力/碰撞穿透，但允许窒息伤害 */
    private static final Set<UUID> PHASE_STALLED = ConcurrentHashMap.newKeySet();
    /** 穿墙尝试剩余 tick，超时未进入墙体则取消 noClip */
    private static final Map<UUID, Integer> PHASE_ATTEMPT_TICKS = new ConcurrentHashMap<>();
    /** 穿墙锁定时的固定视角 */
    private static final Map<UUID, RotationSnapshot> PHASE_LOCKED_ROTATIONS = new ConcurrentHashMap<>();
    /** 进入穿墙前玩家原本的无重力状态，用于退出穿墙后恢复 */
    private static final Map<UUID, Boolean> PHASE_PREVIOUS_NO_GRAVITY = new ConcurrentHashMap<>();
    /** 穿墙黑名单方块，遇到这些方块时不允许开始穿墙 */
    private static final Set<Identifier> PHASE_BLACKLIST = Set.of(
            Identifier.of("minecraft", "bedrock"),
            //Identifier.of("minecraft", "barrier"),
            Identifier.of("minecraft", "command_block"),
            Identifier.of("minecraft", "chain_command_block"),
            Identifier.of("minecraft", "repeating_command_block"),
            //Identifier.of("minecraft", "structure_block"),
            Identifier.of("minecraft", "jigsaw"),
            Identifier.of("minecraft", "end_portal_frame")
    );
    /** 射线允许检测到的最大实体方块数量 */
    private static final int MAX_PHASE_BLOCKS = 3;
    /** 穿墙尝试窗口，避免玩家只是看着墙就永久 noClip */
    private static final int PHASE_ATTEMPT_MAX_TICKS = 30;
    /** 必须贴近墙体才开始穿墙尝试，避免远处墙体提前消耗尝试窗口 */
    private static final double PHASE_START_DISTANCE = 0.95D;
    /** 四条穿墙检测射线相对玩家中心的左右偏移，略小于玩家碰撞箱半宽 */
    private static final double PHASE_RAY_SIDE_OFFSET = 0.3D;
    /** 射线没有命中任何实体墙体，仅打到空气 */
    private static final double PHASE_RAY_NO_HIT = -1.0D;
    /** 单条射线近处命中的墙体过厚，整体否决穿墙 */
    private static final double PHASE_RAY_TOO_THICK = -2.0D;
    /** 单条射线命中黑名单方块，整体否决穿墙 */
    private static final double PHASE_RAY_BLOCKED = Double.MAX_VALUE;
    /** 远处障碍检测余量，防止头部刚穿完合法墙体后腿部立刻进入厚墙 */
    private static final double PHASE_OBSTRUCTION_CLEARANCE = 0.75D;
    /** 全局配置管理器，由外部在initialize()时注入 */
    private static GlobalConfigManager configManager;

    private record RotationSnapshot(float yaw, float pitch) {}

    private record PhaseRayResult(double firstSolidDistance, double exitDistance, int solidBlocks, boolean blocked, boolean tooThick) {
        private boolean hasSolid() {
            return firstSolidDistance >= 0.0D;
        }

        private boolean isNear() {
            return hasSolid() && firstSolidDistance <= PHASE_START_DISTANCE;
        }

        private boolean isValidNearWall() {
            return isNear() && !blocked && !tooThick;
        }

        private boolean isBlockingAtOrBefore(double distance) {
            return hasSolid() && firstSolidDistance <= distance && !isValidNearWall();
        }
    }

    public ThroughItem(Settings settings) {
        super(settings);
    }

    /**
     * 右键使用隐秘物品，进入隐秘准备状态。
     * 仅主手持有隐秘物品时生效；服务端检查Silenced标签和阶段配置后，
     * 若未处于隐秘状态则进入隐秘准备阶段（施加失明/黑暗，延迟后隐身）。
     */
    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        if (hand != Hand.MAIN_HAND || !isHoldingSecrecy(user.getStackInHand(hand))) {
            return ActionResult.PASS;
        }

        if (!world.isClient() && user instanceof ServerPlayerEntity player) {
            if (!canUseSecrecy(player, true)) {
                return ActionResult.FAIL;
            }
            if (PHASE_STALLED.contains(player.getUuid())) {
                resumePhaseLocked(player);
                user.setCurrentHand(hand);
                return ActionResult.SUCCESS;
            }
            user.setCurrentHand(hand);
            if (isSecrecyActive(player)) {
                enterSecrecy(player); int stage = getPlayerStage(player);
                ThroughConfig config = ThroughConfig.getInstance();
                int delaySeconds = config.getVanishDelaySeconds(stage);
                player.sendMessage(Text.literal("§b正在集中精神..."+"("+delaySeconds+"秒)"), true);
            }
        } else {
            user.setCurrentHand(hand);
        }
        return ActionResult.SUCCESS;
    }

    /**
     * 使用过程中的每tick回调。
     * 服务端检查：若玩家尚未进入隐秘状态且满足条件，则自动进入隐秘。
     * 若玩家在使用过程中失去使用资格（如被添加Silenced标签），则强制停止使用。
     */
    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        if (!world.isClient() && user instanceof ServerPlayerEntity player && isSecrecyActive(player)) {
            if (!canUseSecrecy(player, true)) {
                player.stopUsingItem();
                return;
            }
            enterSecrecy(player);
            player.sendMessage(Text.literal("§b正在集中精神..."), true);
        }
    }

    /**
     * 玩家停止使用物品时的回调（松开右键）。
     * 服务端调用exitSecrecy退出隐秘状态。
     */
    @Override
    public boolean onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (!world.isClient() && user instanceof ServerPlayerEntity player) {
            if (PHASE_LOCKED.contains(player.getUuid())) {
                enterPhaseStalled(player);
                return true;
            }
            exitSecrecy(player);
        }
        return true;
    }

    /** 物品最大使用时间（tick），72000 tick ≈ 60分钟，即几乎等同于无限使用 */
    @Override
    public int getMaxUseTime(ItemStack stack, LivingEntity user) {
        return 72000;
    }

    /** 使用动作表现为拉弓姿势，与右键长按体感一致 */
    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.BOW;
    }

    /**
     * 静态初始化：注册心跳音效、创建隐秘物品实例并注册到物品注册表，
     * 同时注册服务端tick事件处理隐秘状态的持续逻辑和消失延迟倒计时。
     * @param manager 全局配置管理器，用于读取各阶段的隐秘参数
     */
    public static void initialize(GlobalConfigManager manager) {
        configManager = manager;
        HEART_SOUND = Registry.register(Registries.SOUND_EVENT, HEART_SOUND_ID, SoundEvent.of(HEART_SOUND_ID));
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, ID);
        THROUGH_ITEM = new ThroughItem(new Item.Settings().registryKey(key).maxCount(1));
        Registry.register(Registries.ITEM, ID, THROUGH_ITEM);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(THROUGH_ITEM));

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long now = server.getOverworld().getTime();
            Set<UUID> activePlayers = new HashSet<>();
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (EXITING_SECRECY.remove(player.getUuid())) {
                    continue;
                }
                if (PHASE_STALLED.contains(player.getUuid())) {
                    tickPhaseStalled(player);
                    activePlayers.add(player.getUuid());
                    playHeartSound(player);
                    continue;
                }
                if (PHASE_LOCKED.contains(player.getUuid())) {
                    tickPhaseLocked(player);
                    activePlayers.add(player.getUuid());
                    playHeartSound(player);
                    continue;
                }
                if (PHASE_ATTEMPTING.contains(player.getUuid())) {
                    tickPhaseAttempting(player);
                } else if (PHASE_READY.contains(player.getUuid())) {
                    tickPhaseReady(player);
                }
                if (isSecrecyActive(player)) continue;
                if (!shouldContinueSecrecy(player)) {
                    exitSecrecy(player);
                    continue;
                }
                activePlayers.add(player.getUuid());
                playHeartSound(player);
            }
            HEART_SOUND_TICKS.keySet().removeIf(uuid -> {
                if (activePlayers.contains(uuid)) {
                    return false;
                }
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
                if (player != null) {
                    stopHeartSound(player);
                }
                return true;
            });

            Iterator<Map.Entry<UUID, Long>> iterator = VANISH_PENDING_TICKS.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, Long> entry = iterator.next();
                if (entry.getValue() > now) continue;
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
                if (player != null && shouldContinueSecrecy(player)) {
                    //开关隐身
                    //player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, INFINITE_DURATION, 0, false, false, true));
                    PHASE_READY.add(player.getUuid());
                    syncSecrecyState(player, true, false, false);
                    player.sendMessage(Text.literal("§b精神集中..."), true);
                }
                iterator.remove();
            }
        });
    }

    /** 检查物品堆是否就是隐秘物品本身 */
    public static boolean isHoldingSecrecy(ItemStack stack) {
        return THROUGH_ITEM != null && stack.getItem() == THROUGH_ITEM;
    }

    /**
     * 获取玩家的阶段值（1-8），用于查询对应阶段的隐秘配置参数。
     * @return 阶段值，若配置管理器未初始化则默认返回1
     */
    public static int getPlayerStage(ServerPlayerEntity player) {
        if (configManager == null) return 1;
        return com.kuilunfuzhe.monvhua.features.evil_eyes.Evil_Eyes.getPlayerStage(player, configManager);
    }

    /**
     * 强制退出隐秘状态。
     * 清除玩家的消失等待、心跳计时、速度修正、隐身/失明/黑暗效果，
     * 并将玩家加入EXITING_SECRECY集合以在下一tick跳过处理。
     */
    public static void exitSecrecy(ServerPlayerEntity player) {
        if (isSecrecyActive(player)) {
            return;
        }
        UUID uuid = player.getUuid();
        VANISH_PENDING_TICKS.remove(uuid);
        HEART_SOUND_TICKS.remove(uuid);
        stopHeartSound(player);
        ACTIVE_SECRECY.remove(uuid);
        EXITING_SECRECY.add(uuid);
        clearPhaseState(player);
        removeSpeedModifier(player);
        //player.removeStatusEffect(StatusEffects.INVISIBILITY);
        //player.removeStatusEffect(StatusEffects.BLINDNESS);
        //player.removeStatusEffect(StatusEffects.DARKNESS);
        syncSecrecyState(player, false, false, false);
    }

    private static void enterSecrecy(ServerPlayerEntity player) {
        int stage = getPlayerStage(player);
        ThroughConfig config = ThroughConfig.getInstance();
        double speedMultiplier = config.getSpeedMultiplier(stage);
        int delaySeconds = config.getVanishDelaySeconds(stage);
        int delayTicks = delaySeconds * 20;

        ACTIVE_SECRECY.add(player.getUuid());
        applySpeedMultiplier(player, speedMultiplier);
        playHeartSound(player);
        EXITING_SECRECY.remove(player.getUuid());
        //player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, INFINITE_DURATION, 0, false, false, false));
        //player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, INFINITE_DURATION, 0, false, false, false));
        //player.removeStatusEffect(StatusEffects.INVISIBILITY);
        syncSecrecyState(player, false, false, false);
        VANISH_PENDING_TICKS.put(player.getUuid(), player.getWorld().getTime() + delayTicks);
    }

    private static boolean isSecrecyActive(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        return !ACTIVE_SECRECY.contains(uuid)
                && !VANISH_PENDING_TICKS.containsKey(uuid)
                && !hasSpeedModifier(player);
    }

    private static boolean hasPreparationEffects(ServerPlayerEntity player) {
        return player.hasStatusEffect(StatusEffects.BLINDNESS)
                && player.hasStatusEffect(StatusEffects.DARKNESS);
    }

    private static boolean shouldContinueSecrecy(ServerPlayerEntity player) {
        if (PHASE_STALLED.contains(player.getUuid())) {
            return isHoldingSecrecy(player.getMainHandStack()) && canUseSecrecy(player, false);
        }
        if (PHASE_LOCKED.contains(player.getUuid())) {
            return isHoldingSecrecy(player.getMainHandStack()) && canUseSecrecy(player, false);
        }
        return player.isUsingItem()
                && player.getActiveHand() == Hand.MAIN_HAND
                && isHoldingSecrecy(player.getMainHandStack())
                && canUseSecrecy(player, false);
    }

    private static boolean canUseSecrecy(ServerPlayerEntity player, boolean notify) {
        if (player.getCommandTags().contains(SILENCED_TAG)) {
            if (notify) {
                player.sendMessage(Text.literal("§c受到干扰"), true);
            }
            return false;
        }

        int stage = getPlayerStage(player);
        if (ThroughConfig.getInstance().getVanishDelaySeconds(stage) < 0) {
            if (notify) {
                player.sendMessage(Text.literal("§c受到干扰"), true);
            }
            return false;
        }
        return true;
    }

    private static void applySpeedMultiplier(ServerPlayerEntity player, double multiplier) {
        EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
        if (speed == null) return;
        speed.removeModifier(SPEED_MODIFIER_ID);
        speed.addTemporaryModifier(new EntityAttributeModifier(
                SPEED_MODIFIER_ID,
                Math.max(0.0D, multiplier) - 1.0D,
                EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        ));
    }

    private static void removeSpeedModifier(ServerPlayerEntity player) {
        EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SPEED_MODIFIER_ID);
        }
    }

    private static boolean hasSpeedModifier(ServerPlayerEntity player) {
        EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
        return speed != null && speed.hasModifier(SPEED_MODIFIER_ID);
    }

    private static void playHeartSound(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        int ticks = HEART_SOUND_TICKS.getOrDefault(uuid, 0);
        if (ticks > 0) {
            HEART_SOUND_TICKS.put(uuid, ticks - 1);
            return;
        }
        player.playSoundToPlayer(HEART_SOUND, SoundCategory.PLAYERS, HEART_SOUND_MAX_VOLUME, 1.0F);
        HEART_SOUND_TICKS.put(uuid, HEART_SOUND_INTERVAL_TICKS);
    }

    private static void stopHeartSound(ServerPlayerEntity player) {
        player.networkHandler.sendPacket(new StopSoundS2CPacket(HEART_SOUND_ID, SoundCategory.PLAYERS));
    }

    private static void tickPhaseReady(ServerPlayerEntity player) {
        if (!shouldContinueSecrecy(player)) {
            return;
        }
        if (!canAttemptPhase(player) || getPhaseWallDistance(player) > PHASE_START_DISTANCE) {
            return;
        }

        UUID uuid = player.getUuid();
        PHASE_ATTEMPTING.add(uuid);
        PHASE_ATTEMPT_TICKS.put(uuid, PHASE_ATTEMPT_MAX_TICKS);
        enablePhaseGravityLock(player);
        player.noClip = false;
        player.fallDistance = 0.0F;
        syncSecrecyState(player, true, true, false);
    }

    private static void tickPhaseAttempting(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        if (!shouldContinueSecrecy(player)) {
            PHASE_ATTEMPTING.remove(uuid);
            PHASE_ATTEMPT_TICKS.remove(uuid);
            restorePhaseGravityLock(player);
            player.noClip = false;
            syncSecrecyState(player, true, false, false);
            return;
        }

        player.noClip = false;
        player.fallDistance = 0.0F;
        zeroVerticalVelocity(player);
        clampPhaseHorizontalSpeed(player);

        if (isInsideWall(player)) {
            enterPhaseLocked(player);
            return;
        }

        int ticks = PHASE_ATTEMPT_TICKS.getOrDefault(uuid, 0) - 1;
        if (ticks <= 0) {
            PHASE_ATTEMPTING.remove(uuid);
            PHASE_ATTEMPT_TICKS.remove(uuid);
            restorePhaseGravityLock(player);
            player.noClip = false;
            syncSecrecyState(player, true, false, false);
            return;
        }
        PHASE_ATTEMPT_TICKS.put(uuid, ticks);
    }

    private static void enterPhaseLocked(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        PHASE_ATTEMPTING.remove(uuid);
        PHASE_ATTEMPT_TICKS.remove(uuid);
        PHASE_READY.remove(uuid);
        PHASE_STALLED.remove(uuid);
        PHASE_LOCKED.add(uuid);
        PHASE_LOCKED_ROTATIONS.put(uuid, new RotationSnapshot(player.getYaw(), player.getPitch()));
        enablePhaseGravityLock(player);
        player.noClip = false;
        player.fallDistance = 0.0F;
        syncSecrecyState(player, true, true, true);
        player.sendMessage(Text.literal("§b集中..."), true);
    }

    private static void tickPhaseLocked(ServerPlayerEntity player) {
        if (!shouldContinueSecrecy(player)) {
            enterPhaseStalled(player);
            return;
        }

        player.noClip = false;
        player.fallDistance = 0.0F;
        enablePhaseGravityLock(player);
        zeroVerticalVelocity(player);
        applySpeedMultiplier(player, ThroughConfig.getInstance().getSpeedMultiplier(getPlayerStage(player)));
        restoreLockedRotation(player);
        constrainLockedVelocity(player);

        if (isInsideWall(player)) {
            return;
        }

        exitPhaseLocked(player);
        if (!shouldContinueSecrecy(player)) {
            exitSecrecy(player);
        }
    }

    private static void enterPhaseStalled(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        PHASE_LOCKED.remove(uuid);
        PHASE_ATTEMPTING.remove(uuid);
        PHASE_ATTEMPT_TICKS.remove(uuid);
        PHASE_READY.remove(uuid);
        PHASE_STALLED.add(uuid);
        PHASE_LOCKED_ROTATIONS.putIfAbsent(uuid, new RotationSnapshot(player.getYaw(), player.getPitch()));
        enablePhaseGravityLock(player);
        player.noClip = false;
        player.fallDistance = 0.0F;
        stopPhaseMovement(player);
        syncSecrecyState(player, true, true, true, true);
    }

    private static void tickPhaseStalled(ServerPlayerEntity player) {
        player.noClip = false;
        player.fallDistance = 0.0F;
        enablePhaseGravityLock(player);
        restoreLockedRotation(player);
        stopPhaseMovement(player);

        if (isInsideWall(player)) {
            return;
        }
        exitSecrecy(player);
    }

    private static void resumePhaseLocked(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        PHASE_STALLED.remove(uuid);
        PHASE_READY.remove(uuid);
        PHASE_ATTEMPTING.remove(uuid);
        PHASE_ATTEMPT_TICKS.remove(uuid);
        PHASE_LOCKED.add(uuid);
        PHASE_LOCKED_ROTATIONS.putIfAbsent(uuid, new RotationSnapshot(player.getYaw(), player.getPitch()));
        enablePhaseGravityLock(player);
        player.noClip = false;
        player.fallDistance = 0.0F;
        stopPhaseMovement(player);
        syncSecrecyState(player, true, true, true, false);
        player.sendMessage(Text.literal("§b集中..."), true);
    }

    private static void exitPhaseLocked(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        PHASE_LOCKED.remove(uuid);
        PHASE_LOCKED_ROTATIONS.remove(uuid);
        restorePhaseGravityLock(player);
        PHASE_READY.add(uuid);
        player.noClip = false;
        syncSecrecyState(player, true, false, false);
    }

    private static void clearPhaseState(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        PHASE_READY.remove(uuid);
        PHASE_ATTEMPTING.remove(uuid);
        PHASE_LOCKED.remove(uuid);
        PHASE_STALLED.remove(uuid);
        PHASE_ATTEMPT_TICKS.remove(uuid);
        PHASE_LOCKED_ROTATIONS.remove(uuid);
        restorePhaseGravityLock(player);
        player.noClip = false;
    }

    private static boolean canAttemptPhase(ServerPlayerEntity player) {
        return getPhaseWallDistance(player) != Double.MAX_VALUE;
    }

    private static double getPhaseWallDistance(ServerPlayerEntity player) {
        Vec3d horizontalLook = getHorizontalLook(player);
        if (horizontalLook == null) {
            return Double.MAX_VALUE;
        }

        Vec3d right = new Vec3d(horizontalLook.z, 0.0D, -horizontalLook.x).normalize();
        Vec3d feetLeft = new Vec3d(player.getX(), player.getY() + 0.1D, player.getZ()).subtract(right.multiply(PHASE_RAY_SIDE_OFFSET));
        Vec3d feetRight = new Vec3d(player.getX(), player.getY() + 0.1D, player.getZ()).add(right.multiply(PHASE_RAY_SIDE_OFFSET));
        Vec3d headLeft = new Vec3d(player.getX(), player.getEyeY(), player.getZ()).subtract(right.multiply(PHASE_RAY_SIDE_OFFSET));
        Vec3d headRight = new Vec3d(player.getX(), player.getEyeY(), player.getZ()).add(right.multiply(PHASE_RAY_SIDE_OFFSET));

        PhaseRayResult feetLeftResult = getPhaseRayWallResult(player, feetLeft, horizontalLook);
        PhaseRayResult feetRightResult = getPhaseRayWallResult(player, feetRight, horizontalLook);
        PhaseRayResult headLeftResult = getPhaseRayWallResult(player, headLeft, horizontalLook);
        PhaseRayResult headRightResult = getPhaseRayWallResult(player, headRight, horizontalLook);

        double phaseDistance = combinePhaseRayDistances(
                feetLeftResult,
                feetRightResult,
                headLeftResult,
                headRightResult
        );
        return phaseDistance;
    }

    private static double combinePhaseRayDistances(PhaseRayResult... results) {
        double farthestValidStartDistance = PHASE_RAY_NO_HIT;
        double farthestValidExitDistance = PHASE_RAY_NO_HIT;
        for (PhaseRayResult result : results) {
            if (result.isNear() && (result.blocked() || result.tooThick())) {
                return PHASE_RAY_BLOCKED;
            }
            if (result.isValidNearWall()) {
                farthestValidStartDistance = Math.max(farthestValidStartDistance, result.firstSolidDistance());
                farthestValidExitDistance = Math.max(farthestValidExitDistance, result.exitDistance());
            }
        }

        if (farthestValidStartDistance < 0.0D) {
            return PHASE_RAY_BLOCKED;
        }

        double obstructionLimit = farthestValidExitDistance + PHASE_OBSTRUCTION_CLEARANCE;
        for (PhaseRayResult result : results) {
            if (result.isBlockingAtOrBefore(obstructionLimit)) {
                return PHASE_RAY_BLOCKED;
            }
        }

        return farthestValidStartDistance;
    }

    private static PhaseRayResult getPhaseRayWallResult(ServerPlayerEntity player, Vec3d start, Vec3d horizontalLook) {
        Set<BlockPos> checkedBlocks = new HashSet<>();
        int solidBlocks = 0;
        double firstSolidDistance = Double.MAX_VALUE;
        double maxDistance = (MAX_PHASE_BLOCKS * 2.0D) + PHASE_START_DISTANCE + PHASE_OBSTRUCTION_CLEARANCE;

        for (double distance = 0.1D; distance <= maxDistance; distance += 0.1D) {
            BlockPos pos = BlockPos.ofFloored(start.add(horizontalLook.multiply(distance)));
            if (!checkedBlocks.add(pos)) {
                continue;
            }

            BlockState state = player.getWorld().getBlockState(pos);
            if (state.isAir() || state.getCollisionShape(player.getWorld(), pos).isEmpty()) {
                if (solidBlocks > 0) {
                    return new PhaseRayResult(firstSolidDistance, distance, solidBlocks, false, false);
                }
                continue;
            }
            if (solidBlocks == 0) {
                firstSolidDistance = distance;
            }
            if (isPhaseBlacklisted(state)) {
                return new PhaseRayResult(firstSolidDistance, distance, solidBlocks + 1, true, false);
            }
            solidBlocks++;
            if (solidBlocks > MAX_PHASE_BLOCKS) {
                return new PhaseRayResult(firstSolidDistance, distance, solidBlocks, false, true);
            }
        }
        return solidBlocks > 0
                ? new PhaseRayResult(firstSolidDistance, maxDistance, solidBlocks, false, false)
                : new PhaseRayResult(PHASE_RAY_NO_HIT, PHASE_RAY_NO_HIT, 0, false, false);
    }

    private static Vec3d getHorizontalLook(ServerPlayerEntity player) {
        Vec3d look = player.getRotationVec(1.0F);
        Vec3d horizontalLook = new Vec3d(look.x, 0.0D, look.z);
        if (horizontalLook.lengthSquared() < 1.0E-4D) {
            return null;
        }
        return horizontalLook.normalize();
    }

    private static boolean isPhaseBlacklisted(BlockState state) {
        return PHASE_BLACKLIST.contains(Registries.BLOCK.getId(state.getBlock()));
    }

    private static boolean isInsideWall(ServerPlayerEntity player) {
        var world = player.getWorld();
        var box = player.getBoundingBox();
        BlockPos min = BlockPos.ofFloored(box.minX, box.minY, box.minZ);
        BlockPos max = BlockPos.ofFloored(box.maxX, box.maxY, box.maxZ);

        for (BlockPos pos : BlockPos.iterate(min, max)) {
            BlockState state = world.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            VoxelShape shape = state.getCollisionShape(world, pos);
            if (shape.isEmpty()) {
                continue;
            }
            for (var shapeBox : shape.getBoundingBoxes()) {
                if (shapeBox.offset(pos).intersects(box)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean shouldIgnorePhaseCollision(ServerPlayerEntity player, BlockPos pos) {
        if (!shouldIgnorePhaseCollision(player)) {
            return false;
        }
        int feetY = BlockPos.ofFloored(player.getX(), player.getY() + 0.05D, player.getZ()).getY();
        int headY = BlockPos.ofFloored(player.getBoundingBox().maxX, player.getBoundingBox().maxY, player.getBoundingBox().maxZ).getY();
        return pos.getY() >= feetY && pos.getY() <= headY;
    }

    private static boolean shouldIgnorePhaseCollision(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        return PHASE_ATTEMPTING.contains(uuid) || PHASE_LOCKED.contains(uuid) || PHASE_STALLED.contains(uuid);
    }

    public static boolean isPhaseNoClip(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        return PHASE_ATTEMPTING.contains(uuid) || PHASE_LOCKED.contains(uuid);
    }

    public static boolean isPhaseLocked(ServerPlayerEntity player) {
        return PHASE_LOCKED.contains(player.getUuid()) || PHASE_STALLED.contains(player.getUuid());
    }

    private static void enablePhaseGravityLock(ServerPlayerEntity player) {
        PHASE_PREVIOUS_NO_GRAVITY.putIfAbsent(player.getUuid(), player.hasNoGravity());
        player.setNoGravity(true);
        player.fallDistance = 0.0F;
    }

    private static void restorePhaseGravityLock(ServerPlayerEntity player) {
        Boolean previousNoGravity = PHASE_PREVIOUS_NO_GRAVITY.remove(player.getUuid());
        if (previousNoGravity != null) {
            player.setNoGravity(previousNoGravity);
        }
        player.fallDistance = 0.0F;
    }

    private static void zeroVerticalVelocity(ServerPlayerEntity player) {
        Vec3d velocity = player.getVelocity();
        if (velocity.y != 0.0D) {
            player.setVelocity(velocity.x, 0.0D, velocity.z);
        }
    }

    private static void stopPhaseMovement(ServerPlayerEntity player) {
        Vec3d velocity = player.getVelocity();
        if (velocity.lengthSquared() != 0.0D) {
            player.setVelocity(Vec3d.ZERO);
        }
    }

    private static void clampPhaseHorizontalSpeed(ServerPlayerEntity player) {
        Vec3d velocity = player.getVelocity();
        double horizontalSpeedSquared = (velocity.x * velocity.x) + (velocity.z * velocity.z);
        double maxSpeed = getCurrentMovementSpeed(player);
        double maxSpeedSquared = maxSpeed * maxSpeed;
        if (horizontalSpeedSquared > maxSpeedSquared && horizontalSpeedSquared > 0.0D) {
            double scale = maxSpeed / Math.sqrt(horizontalSpeedSquared);
            player.setVelocity(velocity.x * scale, velocity.y, velocity.z * scale);
        }
    }

    private static double getCurrentMovementSpeed(ServerPlayerEntity player) {
        int stage = getPlayerStage(player);
        ThroughConfig config = ThroughConfig.getInstance();
        double speedMultiplier = config.getSpeedMultiplier(stage);
        EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
        return speed == null ? speedMultiplier : Math.max(0.0D, speedMultiplier);
    }

    public static void restoreLockedRotation(ServerPlayerEntity player) {
        RotationSnapshot rotation = PHASE_LOCKED_ROTATIONS.get(player.getUuid());
        if (rotation == null) {
            return;
        }
        player.setYaw(rotation.yaw());
        player.setPitch(rotation.pitch());
    }

    private static void constrainLockedVelocity(ServerPlayerEntity player) {
        RotationSnapshot rotation = PHASE_LOCKED_ROTATIONS.get(player.getUuid());
        if (rotation == null) {
            return;
        }
        double yawRadians = Math.toRadians(rotation.yaw());
        Vec3d forward = new Vec3d(-Math.sin(yawRadians), 0.0D, Math.cos(yawRadians));
        Vec3d velocity = player.getVelocity();
        double forwardSpeed = velocity.x * forward.x + velocity.z * forward.z;
        double maxSpeed = getCurrentMovementSpeed(player);
        forwardSpeed = Math.clamp(forwardSpeed, -maxSpeed, maxSpeed);
        player.setVelocity(forward.multiply(forwardSpeed));
    }

    private static void syncSecrecyState(ServerPlayerEntity player, boolean invisible, boolean phaseNoClip, boolean phaseLocked) {
        syncSecrecyState(player, invisible, phaseNoClip, phaseLocked, false);
    }

    private static void syncSecrecyState(ServerPlayerEntity player, boolean invisible, boolean phaseNoClip, boolean phaseLocked, boolean phaseStalled) {
        RotationSnapshot rotation = PHASE_LOCKED_ROTATIONS.get(player.getUuid());
        float yaw = rotation != null ? rotation.yaw() : player.getYaw();
        float pitch = rotation != null ? rotation.pitch() : player.getPitch();
        double speedMultiplier = ThroughConfig.getInstance().getSpeedMultiplier(getPlayerStage(player));
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                player,
                new ThroughStateS2CPacket(invisible, phaseNoClip, phaseLocked, phaseStalled, yaw, pitch, speedMultiplier, 0)
        );
    }
}
