package com.kuilunfuzhe.monvhua.features.floating;

import com.kuilunfuzhe.monvhua.config.GlobalConfigManager;
import com.kuilunfuzhe.monvhua.item.config.FloatingConfig;
import com.kuilunfuzhe.monvhua.network.general_stage.GeneralStagePackets.GlobalConfigS2C;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import com.kuilunfuzhe.monvhua.MonvhuaMod;

public class floating {

    private static final String FULL_WITCH_TAG = "MonvhuaFull";
    private static final String FLOATING_TAG = "Floating";  // ← 新增

    // 双击相关
    private static long lastJumpTime = 0;
    private static boolean isFloating = false;
    private static final long DOUBLE_JUMP_INTERVAL = 300;
    private static final int FULL_WITCH_SCORE = 90;

    // 缓降计时器（用于分数>=90时持续给缓降）
    private static java.util.Map<java.util.UUID, Integer> slowFallingTimer = new java.util.HashMap<>();
    private static final java.util.Map<java.util.UUID, AbilitySnapshot> fullWitchFlightSnapshots = new java.util.HashMap<>();

    // 能量系统
    private static final int MAX_ENERGY = 100;
    private static final float DEFAULT_FLY_SPEED = 0.05f;
    private static final float CREATIVE_FLY_SPEED = 0.1f;
    private static java.util.Map<java.util.UUID, Double> playerEnergy = new java.util.HashMap<>();
    private static boolean clientHasFullWitchTag = false;
    private static boolean clientHasFullWitchFlight = false;
    private static boolean clientHasFloatingTag = false;  // ← 新增
    private static GlobalConfigManager configManager;
    private static final int STAGES = 7;
    private static final int[] DEFAULT_STAGE_MIN = {0, 0, 6, 21, 41, 61, 71, 81};
    private static final int[] DEFAULT_STAGE_MAX = {0, 5, 20, 40, 60, 70, 80, 100};
    private static final int[] clientStageMin = DEFAULT_STAGE_MIN.clone();
    private static final int[] clientStageMax = DEFAULT_STAGE_MAX.clone();
    private static boolean clientServerFloating = false;
    private static boolean clientNeedsFloatingStopSync = false;

    private record AbilitySnapshot(boolean allowFlying, boolean flying, float flySpeed) {}

    // ==================== 计分板读取 ====================

    public static void initialize(GlobalConfigManager manager) {
        configManager = manager;
    }

    public static void syncStageRanges(GlobalConfigS2C.StageConfig[] configs) {
        if (configs == null) return;
        for (int i = 0; i < configs.length && i < STAGES; i++) {
            GlobalConfigS2C.StageConfig cfg = configs[i];
            if (cfg == null) continue;
            clientStageMin[i + 1] = cfg.minScore();
            clientStageMax[i + 1] = cfg.maxScore();
        }
    }

    public static void resetStageRanges() {
        System.arraycopy(DEFAULT_STAGE_MIN, 0, clientStageMin, 0, DEFAULT_STAGE_MIN.length);
        System.arraycopy(DEFAULT_STAGE_MAX, 0, clientStageMax, 0, DEFAULT_STAGE_MAX.length);
    }

    private static int getMonvhuaScore(PlayerEntity player) {
        Scoreboard scoreboard = player.getScoreboard();
        ScoreboardObjective objective = scoreboard.getNullableObjective("monvhua");
        if (objective == null) return 1;
        var score = scoreboard.getScore(player, objective);
        if (score == null) return 1;
        return Math.clamp(score.getScore(), 0, 100);
    }

    private static int getStageByScore(PlayerEntity player, int score) {
        if (player instanceof ServerPlayerEntity && configManager != null) {
            return Math.clamp(configManager.getStageByScore(score), 1, STAGES);
        }

        for (int stage = STAGES; stage >= 1; stage--) {
            if (score >= clientStageMin[stage] && score <= clientStageMax[stage]) {
                return stage;
            }
        }
        return 1;
    }

    private static boolean hasFullWitchTag(PlayerEntity player) {
        if (player instanceof ServerPlayerEntity) {
            return player.getCommandTags().contains(FULL_WITCH_TAG);
        }
        return clientHasFullWitchTag;
    }

    // ===== 新增：Floating 标签检测方法 =====
    private static boolean hasFloatingTag(PlayerEntity player) {
        if (player instanceof ServerPlayerEntity) {
            return player.getCommandTags().contains(FLOATING_TAG);
        }
        return clientHasFloatingTag;
    }

    public static void syncFullWitchTag(boolean hasTag) {
        syncFullWitchTag(hasTag, false);
    }

    public static void syncFullWitchTag(boolean hasTag, boolean hasFlight) {
        clientHasFullWitchTag = hasTag;
        clientHasFullWitchFlight = hasFlight;
    }

    // ===== 新增：Floating 标签同步方法 =====
    public static void syncFloatingTag(boolean hasTag) {
        clientHasFloatingTag = hasTag;
    }

    public static void syncFloatingActive(boolean active) {
        clientServerFloating = active;
    }

    public static boolean consumeFloatingStopSync() {
        boolean result = clientNeedsFloatingStopSync;
        clientNeedsFloatingStopSync = false;
        return result;
    }

    public static boolean hasFullWitchFlight(PlayerEntity player) {
        if (player instanceof ServerPlayerEntity) {
            return  getMonvhuaScore(player) >= FULL_WITCH_SCORE
                    && hasFullWitchTag(player)
                    && hasFloatingTag(player)
                    && getEnergy(player) > 0
                    && !player.isCreative()
                    && !player.isSpectator();
        }
        return clientHasFullWitchFlight && !player.isCreative() && !player.isSpectator();
    }

    private static void updateFullWitchFlight(PlayerEntity player) {
        java.util.UUID uuid = player.getUuid();

        if (hasFullWitchFlight(player)) {
            fullWitchFlightSnapshots.computeIfAbsent(uuid, ignored -> new AbilitySnapshot(
                    player.getAbilities().allowFlying,
                    player.getAbilities().flying,
                    player.getAbilities().getFlySpeed()
            ));

            isFloating = false;
            if (player instanceof ServerPlayerEntity) {
                setServerFloating(uuid, false);
            }
            boolean changed = !player.getAbilities().allowFlying
                    || player.getAbilities().getFlySpeed() != CREATIVE_FLY_SPEED;
            player.getAbilities().allowFlying = true;
            player.getAbilities().setFlySpeed(CREATIVE_FLY_SPEED);
            player.fallDistance = 0;
            if (changed) {
                player.sendAbilitiesUpdate();
            }
            return;
        }

        boolean hadFullWitchFlight = fullWitchFlightSnapshots.remove(uuid) != null;
        if (!hadFullWitchFlight || player.isCreative() || player.isSpectator()) return;

        if (isFloatingServer(uuid) || isFloating) {
            player.getAbilities().allowFlying = true;
            player.getAbilities().setFlySpeed(getConfiguredFlySpeed(player));
        } else {
            player.getAbilities().allowFlying = false;
            player.getAbilities().flying = false;
            player.getAbilities().setFlySpeed(DEFAULT_FLY_SPEED);
        }
        player.sendAbilitiesUpdate();
    }

    public static boolean shouldPreventFallDamage(ServerPlayerEntity player) {
        int score = getMonvhuaScore(player);
        if (score >= FULL_WITCH_SCORE) {
            return true;
        }
        return isFloatingServer(player.getUuid());
    }

    // ==================== 服务端漂浮状态管理 ====================

    public static boolean isFloatingServer(java.util.UUID uuid) {
        return MonvhuaMod.floatingPlayersServer.contains(uuid);
    }

    public static void setServerFloating(java.util.UUID uuid, boolean floating) {
        if (floating) {
            MonvhuaMod.floatingPlayersServer.add(uuid);
        } else {
            MonvhuaMod.floatingPlayersServer.remove(uuid);
        }
    }

    // ==================== 能量系统方法 ====================

    public static double getMaxEnergy() {
        return FloatingConfig.getInstance().maxEnergy;
    }

    public static double getEnergy(PlayerEntity player) {
        return playerEnergy.getOrDefault(player.getUuid(), getMaxEnergy());
    }

    public static void setEnergy(PlayerEntity player, double energy) {
        double maxEnergy = getMaxEnergy();
        if (energy > maxEnergy) energy = maxEnergy;
        if (energy < 0) energy = 0;
        playerEnergy.put(player.getUuid(), energy);
    }

    private static double getEnergyDrainRate(PlayerEntity player) {
        int score = getMonvhuaScore(player);

        int stage = getStageByScore(player, score);
        return FloatingConfig.getInstance().getEnergyDrain(stage);
    }

    private static double getEnergyRegenRate(PlayerEntity player) {
        int score = getMonvhuaScore(player);
        int stage = getStageByScore(player, score);
        return FloatingConfig.getInstance().getEnergyRegen(stage);
    }

    private static float getConfiguredFlySpeed(PlayerEntity player) {
        int score = getMonvhuaScore(player);
        boolean hasTag = hasFullWitchTag(player);

        if (score >= FULL_WITCH_SCORE && hasTag) {
            return CREATIVE_FLY_SPEED;
        }

        int stage = getStageByScore(player, score);
        return FloatingConfig.getInstance().getFlightSpeed(stage);
    }

    public static void tickEnergy(net.minecraft.server.network.ServerPlayerEntity player) {
        java.util.UUID uuid = player.getUuid();
        int score = getMonvhuaScore(player);
        boolean hasTag = hasFullWitchTag(player);
        updateFullWitchFlight(player);

        // 分数 >= 90 的缓降处理
        if (score >= FULL_WITCH_SCORE) {
            if (hasTag) {
                // 有标签：移除缓降，停止计时器
                slowFallingTimer.remove(uuid);
                player.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.SLOW_FALLING);
            } else {
                // 无标签：每 20 tick 给一次缓降 II（1秒，等级1）
                int timer = slowFallingTimer.getOrDefault(uuid, 0);
                timer++;
                if (timer >= 20) {
                    timer = 0;
                    player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                            net.minecraft.entity.effect.StatusEffects.SLOW_FALLING, 20, 1, false, false, true));
                }
                slowFallingTimer.put(uuid, timer);
            }
        }

        boolean isServerFloating = isFloatingServer(uuid);
        boolean fullWitchFlightActive = hasFullWitchFlight(player) && player.getAbilities().flying;
        double currentEnergy = getEnergy(player);

        if (isServerFloating || fullWitchFlightActive) {
            double drainPerSecond = getEnergyDrainRate(player);
            double drainPerTick = drainPerSecond / 20.0;
            currentEnergy -= drainPerTick;

            if (currentEnergy <= 0) {
                currentEnergy = 0;
                setEnergy(player, currentEnergy);
                applyServerFloating(player, false);
                player.sendMessage(Text.literal("§c[漂浮] §f能量耗尽，漂浮解除"), true);
            } else {
                setEnergy(player, currentEnergy);
            }
        } else {
            double regenPerTick = getEnergyRegenRate(player) / 20.0;
            currentEnergy += regenPerTick;
            setEnergy(player, currentEnergy);
        }
    }

    public static boolean canStartFloating(ServerPlayerEntity player) {
        if (player.isCreative() || player.isSpectator()) return false;
        if (!hasFloatingTag(player)) return false;
        if (hasFullWitchFlight(player)) return false;

        return getEnergy(player) > 0;
    }

    public static void applyServerFloating(ServerPlayerEntity player, boolean active) {
        java.util.UUID uuid = player.getUuid();
        if (active && !canStartFloating(player)) {
            active = false;
        }

        setServerFloating(uuid, active);
        if (active) {
            player.fallDistance = 0;
            player.getAbilities().allowFlying = true;
            player.getAbilities().flying = true;
            player.getAbilities().setFlySpeed(getConfiguredFlySpeed(player));
        } else {
            boolean keepFullWitchFlight = hasFullWitchFlight(player);
            player.getAbilities().allowFlying = keepFullWitchFlight;
            player.getAbilities().flying = false;
            player.getAbilities().setFlySpeed(keepFullWitchFlight ? CREATIVE_FLY_SPEED : DEFAULT_FLY_SPEED);
        }
        player.sendAbilitiesUpdate();
    }

    // ==================== 核心逻辑（客户端）====================

    public static void tick(PlayerEntity player) {
        int score = getMonvhuaScore(player);
        boolean hasTag = hasFullWitchTag(player);
        updateFullWitchFlight(player);

        // 分数 >= 90 且无标签时，强制关闭飞行
        if (score >= FULL_WITCH_SCORE && !hasTag) {
            if (isFloating) {
                deactivateFloating(player);
                clientServerFloating = false;
                player.sendMessage(Text.literal("§c[漂浮] §f无法使用（需要完全魔女化）"), true);
            }
            return;
        }

        // 检查服务端漂浮状态，如果不一致则同步关闭
        boolean serverFloating = player instanceof ServerPlayerEntity
                ? isFloatingServer(player.getUuid())
                : clientServerFloating;
        if (isFloating && !serverFloating) {
            isFloating = false;
            player.setVelocity(player.getVelocity().x, 0, player.getVelocity().z);
            player.fallDistance = 0;
            player.getAbilities().allowFlying = hasFullWitchFlight(player);
            player.getAbilities().flying = false;
            player.getAbilities().setFlySpeed(hasFullWitchFlight(player) ? CREATIVE_FLY_SPEED : DEFAULT_FLY_SPEED);
            player.sendAbilitiesUpdate();
            player.sendMessage(Text.literal("§c[漂浮] §f已关闭"), true);
            return;
        }

        // 手动飞行模式
        if (isFloating) {
            player.fallDistance = 0;

            if (player.isOnGround()) {
                player.setVelocity(player.getVelocity().x, 0, player.getVelocity().z);
                player.fallDistance = 0;
                deactivateFloating(player);
                player.sendMessage(Text.literal("§c[漂浮] §f已落地解除"), true);
            }
        }
    }

    public static Boolean onPlayerJump(PlayerEntity player) {
//        System.out.println("§e[调试] onPlayerJump 被调用，玩家：" + player.getName().getString());

        if (player.isCreative() || player.isSpectator()) {
//            System.out.println("§e[调试] 创造或旁观模式，跳过");
            return null;
        }

        // ===== 新增：检查是否有 Floating 标签 =====
        if (!hasFloatingTag(player)) {
//            System.out.println("§e[调试] 没有 Floating 标签，无法激活漂浮");
            return null;
        }

        int score = getMonvhuaScore(player);
        boolean hasTag = hasFullWitchTag(player);

        // 分数 >= 90 且没有 MonvhuaFull 标签时，无法使用飞行
        if (score >= FULL_WITCH_SCORE && !hasTag) {
//            System.out.println("§e[调试] 分数 >= 90 且无 MonvhuaFull 标签，禁止飞行");
            if (isFloating) {
                deactivateFloating(player);
                clientServerFloating = false;
                return false;
            }
            return null;
        }

        if (hasFullWitchFlight(player)) {
            lastJumpTime = 0;
            return null;
        }

        long currentTime = System.currentTimeMillis();
//        System.out.println("§e[调试] 距离上次按空格：" + (currentTime - lastJumpTime) + "ms");

        if (currentTime - lastJumpTime < DOUBLE_JUMP_INTERVAL) {
//            System.out.println("§e[调试] 检测到双击！");
            if (isFloating) {
                deactivateFloating(player);
                clientServerFloating = false;
                lastJumpTime = 0;
                return false;
            } else {
                // 检查能量是否足够
                double currentEnergy = getEnergy(player);
                if (currentEnergy <= 0) {
                    player.sendMessage(Text.literal("§c[漂浮] §f能量不足，无法激活"), true);
//                    System.out.println("§e[调试] 能量不足，无法激活");
                    lastJumpTime = 0;
                    return null;
                }
                activateFloating(player);
                clientServerFloating = true;
                lastJumpTime = 0;
                return true;
            }
        } else {
//            System.out.println("§e[调试] 第一次按空格，记录时间");
            lastJumpTime = currentTime;
        }
        return null;
    }

    private static float getFlySpeedMultiplier(PlayerEntity player) {
        int score = getMonvhuaScore(player);
        boolean hasTag = hasFullWitchTag(player);

        // 拥有 MonvhuaFull 标签且分数 >= 90 时，创造级飞行速度（2倍原版）
        if (score >= FULL_WITCH_SCORE && hasTag) {
            return 1.0f;
        }

        return getConfiguredFlySpeed(player) / DEFAULT_FLY_SPEED;
    }

    public static void activateFloating(PlayerEntity player) {
        if (isFloating) return;

        isFloating = true;
        player.fallDistance = 0;
        player.setVelocity(player.getVelocity().x, 0, player.getVelocity().z);

        float flySpeed = getConfiguredFlySpeed(player);

        player.getAbilities().allowFlying = true;
        player.getAbilities().flying = true;
        player.getAbilities().setFlySpeed(flySpeed);
        player.sendAbilitiesUpdate();

        player.sendMessage(Text.literal("§a[漂浮] §f已激活"), true);
    }

    public static void deactivateFloating(PlayerEntity player) {
        if (!isFloating) return;

        isFloating = false;
        player.setVelocity(player.getVelocity().x, 0, player.getVelocity().z);
        player.fallDistance = 0;

        boolean keepFullWitchFlight = hasFullWitchFlight(player);
        player.getAbilities().allowFlying = keepFullWitchFlight;
        player.getAbilities().flying = false;
        player.getAbilities().setFlySpeed(keepFullWitchFlight ? CREATIVE_FLY_SPEED : DEFAULT_FLY_SPEED);
        player.sendAbilitiesUpdate();

        clientServerFloating = false;
        clientNeedsFloatingStopSync = true;

        player.sendMessage(Text.literal("§c[漂浮] §f已关闭"), true);
    }

    public static boolean isFloating() {
        return isFloating;
    }
}
