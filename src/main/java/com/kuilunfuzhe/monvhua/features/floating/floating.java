package com.kuilunfuzhe.monvhua.features.floating;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import com.kuilunfuzhe.monvhua.MonvhuaMod;

public class floating {

    // 双击相关
    private static long lastJumpTime = 0;
    private static boolean isFloating = false;
    private static final long DOUBLE_JUMP_INTERVAL = 300;
    private static final int FULL_WITCH_SCORE = 90;

    // 缓降标记
    private static boolean hasGivenSlowFalling = false;

    // 能量系统
    private static final int MAX_ENERGY = 100;
    private static final double REGEN_RATE = 10.0;
    private static java.util.Map<java.util.UUID, Double> playerEnergy = new java.util.HashMap<>();

    // ==================== 计分板读取 ====================

    private static int getMonvhuaScore(PlayerEntity player) {
        Scoreboard scoreboard = player.getScoreboard();
        ScoreboardObjective objective = scoreboard.getNullableObjective("monvhua");
        if (objective == null) return 0;
        var score = scoreboard.getScore(player, objective);
        if (score == null) return 0;
        return score.getScore();
    }

    private static boolean hasFullWitchTag(PlayerEntity player) {
        return player.getCommandTags().contains("MonvhuaFull");
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
        if (score < 10) return 10.0;
        if (score < 25) return 8.0;
        if (score < 45) return 6.0;
        if (score < 60) return 4.0;
        if (score < 70) return 2.0;
        if (score < 80) return 1.0;
        if (score < 90) return 0.5;
        return 0;
    }

    public static void tickEnergy(net.minecraft.server.network.ServerPlayerEntity player) {
        java.util.UUID uuid = player.getUuid();

        // 完全魔女化或缓降模式，不处理能量
        if (hasFullWitchTag(player) || getMonvhuaScore(player) >= FULL_WITCH_SCORE) {
            return;
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
                // 通知客户端关闭漂浮
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
        // 完全魔女化：完全不干预
        if (hasFullWitchTag(player)) {
            if (isFloating) {
                isFloating = false;
                player.getAbilities().allowFlying = false;
                player.getAbilities().flying = false;
                player.getAbilities().setFlySpeed(0.05f);
                player.sendAbilitiesUpdate();
            }
            return;
        }

        int score = getMonvhuaScore(player);

        // 缓降模式（分数 >= 90）
        if (score >= FULL_WITCH_SCORE) {
            player.fallDistance = 0;

            if (isFloating) {
                player.setVelocity(player.getVelocity().x, 0, player.getVelocity().z);
                deactivateFloating(player);
            }

            if (!hasGivenSlowFalling) {
                player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                        net.minecraft.entity.effect.StatusEffects.SLOW_FALLING, 600, 0, false, false, true));
                player.sendMessage(Text.literal("§a[缓降] §f已激活 (30秒)"), true);
                hasGivenSlowFalling = true;
            }
            return;
        }

        // 重置缓降标记
        if (hasGivenSlowFalling) {
            hasGivenSlowFalling = false;
        }

        // 手动飞行模式（分数 < 90）
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
        if (hasFullWitchTag(player)) {
            System.out.println("§e[调试] 有 MonvhuaFull 标签，跳过");
            return;
        }

        int score = getMonvhuaScore(player);
        System.out.println("§e[调试] 玩家分数：" + score);

        if (score >= FULL_WITCH_SCORE) {
            System.out.println("§e[调试] 分数 >= 90，缓降模式，跳过");
            return;
        }

        long currentTime = System.currentTimeMillis();
        System.out.println("§e[调试] 距离上次按空格：" + (currentTime - lastJumpTime) + "ms");

        if (currentTime - lastJumpTime < DOUBLE_JUMP_INTERVAL) {
            System.out.println("§e[调试] 检测到双击！");
            if (isFloating) {
                deactivateFloating(player);
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
                // 通知服务端玩家已开始漂浮
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
        if (score <= 0) return 0.3f;
        if (score < 25) return 0.4f;
        if (score < 45) return 0.6f;
        if (score < 60) return 0.8f;
        if (score < 70) return 1.0f;
        if (score < 80) return 1.2f;
        return 1.5f;
    }

    public static void activateFloating(PlayerEntity player) {
        if (isFloating) return;

        isFloating = true;
        player.fallDistance = 0;
        player.setVelocity(player.getVelocity().x, 0, player.getVelocity().z);

        float multiplier = getFlySpeedMultiplier(player);
        float flySpeed = 0.05f * multiplier;

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

        player.getAbilities().allowFlying = false;
        player.getAbilities().flying = false;
        player.getAbilities().setFlySpeed(0.05f);
        player.sendAbilitiesUpdate();

        // 通知服务端玩家已停止漂浮
        setServerFloating(player.getUuid(), false);

        player.sendMessage(Text.literal("§c[漂浮] §f已关闭"), true);
    }

    public static boolean isFloating() {
        return isFloating;
    }
}