package com.kuilunfuzhe.monvhua.features.floating;

import com.kuilunfuzhe.monvhua.config.GlobalConfigManager;
import com.kuilunfuzhe.monvhua.network.evil_eyes.GlobalConfigS2CPacket;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import com.kuilunfuzhe.monvhua.MonvhuaMod;

public class floating {

    private static final String FULL_WITCH_TAG = "MonvhuaFull";

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
    private static final double REGEN_RATE = 10.0;
    private static final float DEFAULT_FLY_SPEED = 0.05f;
    private static final float CREATIVE_FLY_SPEED = 0.1f;
    private static java.util.Map<java.util.UUID, Double> playerEnergy = new java.util.HashMap<>();
    private static boolean clientHasFullWitchTag = false;
    private static boolean clientHasFullWitchFlight = false;
    private static GlobalConfigManager configManager;
    private static final int STAGES = 7;
    private static final int[] DEFAULT_STAGE_MIN = {0, 0, 6, 21, 41, 61, 71, 81};
    private static final int[] DEFAULT_STAGE_MAX = {0, 5, 20, 40, 60, 70, 80, 100};
    private static final double[] STAGE_DRAIN_RATES = {0.0, 30.0, 24.0, 18.0, 12.0, 6.0, 4.0, 1.5};
    private static final float[] STAGE_SPEED_MULTIPLIERS = {0.0f, 0.05f, 0.1f, 0.15f, 0.20f, 0.25f, 0.30f, 0.35f};
    private static final int[] clientStageMin = DEFAULT_STAGE_MIN.clone();
    private static final int[] clientStageMax = DEFAULT_STAGE_MAX.clone();

    private record AbilitySnapshot(boolean allowFlying, boolean flying, float flySpeed) {}

    // ==================== 计分板读取 ====================

    public static void initialize(GlobalConfigManager manager) {
        configManager = manager;
    }

    public static void syncStageRanges(GlobalConfigS2CPacket.StageConfig[] configs) {
        if (configs == null) return;
        for (int i = 0; i < configs.length && i < STAGES; i++) {
            GlobalConfigS2CPacket.StageConfig cfg = configs[i];
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

    public static void syncFullWitchTag(boolean hasTag) {
        syncFullWitchTag(hasTag, false);
    }

    public static void syncFullWitchTag(boolean hasTag, boolean hasFlight) {
        clientHasFullWitchTag = hasTag;
        clientHasFullWitchFlight = hasFlight;
    }

    private static boolean hasFullWitchFlight(PlayerEntity player, int score, boolean hasTag) {
        if (player instanceof ServerPlayerEntity) {
            return score >= FULL_WITCH_SCORE && hasTag && !player.isCreative() && !player.isSpectator();
        }
        return clientHasFullWitchFlight && !player.isCreative() && !player.isSpectator();
    }

    public static boolean hasFullWitchFlight(ServerPlayerEntity player) {
        return hasFullWitchFlight(player, getMonvhuaScore(player), hasFullWitchTag(player));
    }

    private static void updateFullWitchFlight(PlayerEntity player, int score, boolean hasTag) {
        java.util.UUID uuid = player.getUuid();

        if (hasFullWitchFlight(player, score, hasTag)) {
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
            if (changed) {
                player.sendAbilitiesUpdate();
            }
            return;
        }

        AbilitySnapshot snapshot = fullWitchFlightSnapshots.remove(uuid);
        if (snapshot == null || player.isCreative() || player.isSpectator()) return;

        if (isFloatingServer(uuid) || isFloating) {
            player.getAbilities().allowFlying = true;
            player.getAbilities().setFlySpeed(DEFAULT_FLY_SPEED * getFlySpeedMultiplier(player));
        } else {
            player.getAbilities().allowFlying = snapshot.allowFlying();
            player.getAbilities().flying = snapshot.flying();
            player.getAbilities().setFlySpeed(snapshot.flySpeed());
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

    public static double getEnergy(PlayerEntity player) {
        return playerEnergy.getOrDefault(player.getUuid(), (double) MAX_ENERGY);
    }

    public static void setEnergy(PlayerEntity player, double energy) {
        if (energy > MAX_ENERGY) energy = MAX_ENERGY;
        if (energy < 0) energy = 0;
        playerEnergy.put(player.getUuid(), energy);
    }

    private static double getEnergyDrainRate(PlayerEntity player) {
        int score = getMonvhuaScore(player);

        // 分数 >= 90 时，能量消耗为 0
        if (score >= FULL_WITCH_SCORE) {
            return 0;
        }

        int stage = getStageByScore(player, score);
        return STAGE_DRAIN_RATES[stage];
    }

    public static void tickEnergy(net.minecraft.server.network.ServerPlayerEntity player) {
        java.util.UUID uuid = player.getUuid();
        int score = getMonvhuaScore(player);
        boolean hasTag = hasFullWitchTag(player);
        updateFullWitchFlight(player, score, hasTag);

        // 分数 >= 90 时免疫摔落伤害
        if (score >= FULL_WITCH_SCORE) {
            player.fallDistance = 0;
        }

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
        double currentEnergy = getEnergy(player);

        if (isServerFloating) {
            double drainPerSecond = getEnergyDrainRate(player);
            double drainPerTick = drainPerSecond / 20.0;
            currentEnergy -= drainPerTick;

            if (currentEnergy <= 0) {
                currentEnergy = 0;
                setEnergy(player, currentEnergy);
                setServerFloating(uuid, false);
                player.sendMessage(Text.literal("§c[漂浮] §f能量耗尽，漂浮解除"), true);
            } else {
                setEnergy(player, currentEnergy);
            }
        } else {
            double regenPerTick = REGEN_RATE / 20.0;
            currentEnergy += regenPerTick;
            if (currentEnergy > MAX_ENERGY) currentEnergy = MAX_ENERGY;
            setEnergy(player, currentEnergy);
        }
    }

    // ==================== 核心逻辑（客户端）====================

    public static void tick(PlayerEntity player) {
        int score = getMonvhuaScore(player);
        boolean hasTag = hasFullWitchTag(player);
        updateFullWitchFlight(player, score, hasTag);

        // 分数 >= 90 时重置摔落距离（免疫摔落）
        if (score >= FULL_WITCH_SCORE) {
            player.fallDistance = 0;
        }

        // 分数 >= 90 且无标签时，强制关闭飞行
        if (score >= FULL_WITCH_SCORE && !hasTag) {
            if (isFloating) {
                deactivateFloating(player);
                setServerFloating(player.getUuid(), false);
                player.sendMessage(Text.literal("§c[漂浮] §f无法使用（需要完全魔女化）"), true);
            }
            return;
        }

        // 检查服务端漂浮状态，如果不一致则同步关闭
        boolean serverFloating = isFloatingServer(player.getUuid());
        if (isFloating && !serverFloating) {
            isFloating = false;
            player.setVelocity(player.getVelocity().x, 0, player.getVelocity().z);
            player.fallDistance = 0;
            player.getAbilities().allowFlying = hasFullWitchFlight(player, score, hasTag);
            player.getAbilities().flying = false;
            player.getAbilities().setFlySpeed(hasFullWitchFlight(player, score, hasTag) ? CREATIVE_FLY_SPEED : DEFAULT_FLY_SPEED);
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

    public static void onPlayerJump(PlayerEntity player) {
        System.out.println("§e[调试] onPlayerJump 被调用，玩家：" + player.getName().getString());

        if (player.isCreative() || player.isSpectator()) {
            System.out.println("§e[调试] 创造或旁观模式，跳过");
            return;
        }

        int score = getMonvhuaScore(player);
        boolean hasTag = hasFullWitchTag(player);

        // 分数 >= 90 且没有 MonvhuaFull 标签时，无法使用飞行
        if (score >= FULL_WITCH_SCORE && !hasTag) {
            System.out.println("§e[调试] 分数 >= 90 且无 MonvhuaFull 标签，禁止飞行");
            if (isFloating) {
                deactivateFloating(player);
                setServerFloating(player.getUuid(), false);
            }
            return;
        }

        if (hasFullWitchFlight(player, score, hasTag)) {
            updateFullWitchFlight(player, score, hasTag);
            lastJumpTime = 0;
            return;
        }

        long currentTime = System.currentTimeMillis();
        System.out.println("§e[调试] 距离上次按空格：" + (currentTime - lastJumpTime) + "ms");

        if (currentTime - lastJumpTime < DOUBLE_JUMP_INTERVAL) {
            System.out.println("§e[调试] 检测到双击！");
            if (isFloating) {
                deactivateFloating(player);
                setServerFloating(player.getUuid(), false);
            } else {
                // 检查能量是否足够
                double currentEnergy = getEnergy(player);
                if (currentEnergy <= 0) {
                    player.sendMessage(Text.literal("§c[漂浮] §f能量不足，无法激活"), true);
                    System.out.println("§e[调试] 能量不足，无法激活");
                    lastJumpTime = 0;
                    return;
                }
                activateFloating(player);
                setServerFloating(player.getUuid(), true);
            }
            lastJumpTime = 0;
        } else {
            System.out.println("§e[调试] 第一次按空格，记录时间");
            lastJumpTime = currentTime;
        }
    }

    private static float getFlySpeedMultiplier(PlayerEntity player) {
        int score = getMonvhuaScore(player);
        boolean hasTag = hasFullWitchTag(player);

        // 拥有 MonvhuaFull 标签且分数 >= 90 时，创造级飞行速度（2倍原版）
        if (score >= FULL_WITCH_SCORE && hasTag) {
            return 1.0f;
        }

        int stage = getStageByScore(player, score);
        return STAGE_SPEED_MULTIPLIERS[stage];
    }

    public static void activateFloating(PlayerEntity player) {
        if (isFloating) return;

        isFloating = true;
        player.fallDistance = 0;
        player.setVelocity(player.getVelocity().x, 0, player.getVelocity().z);

        float multiplier = getFlySpeedMultiplier(player);
        float flySpeed = DEFAULT_FLY_SPEED * multiplier;

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

        boolean keepFullWitchFlight = hasFullWitchFlight(player, getMonvhuaScore(player), hasFullWitchTag(player));
        player.getAbilities().allowFlying = keepFullWitchFlight;
        player.getAbilities().flying = false;
        player.getAbilities().setFlySpeed(keepFullWitchFlight ? CREATIVE_FLY_SPEED : DEFAULT_FLY_SPEED);
        player.sendAbilitiesUpdate();

        setServerFloating(player.getUuid(), false);

        player.sendMessage(Text.literal("§c[漂浮] §f已关闭"), true);
    }

    public static boolean isFloating() {
        return isFloating;
    }
}
