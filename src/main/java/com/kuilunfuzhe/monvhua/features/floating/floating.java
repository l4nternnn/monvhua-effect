package com.kuilunfuzhe.monvhua.features.floating;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;

public class floating {

    private static long lastJumpTime = 0;
    private static boolean isFloating = false;
    private static final long DOUBLE_JUMP_INTERVAL = 300;
    private static final int FULL_WITCH_SCORE = 90;
    private static boolean hasGivenSlowFalling = false;

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

    public static void tick(PlayerEntity player) {
        // 完全魔女化：完全让出控制
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

        // ===== 缓降模式（分数 >= 90）=====
        if (score >= FULL_WITCH_SCORE) {
            // 关键：每 tick 重置摔落距离，防止落地受伤
            player.fallDistance = 0;

            if (isFloating) {
                player.setVelocity(player.getVelocity().x, 0, player.getVelocity().z);
                deactivateFloating(player);
            }

            if (!hasGivenSlowFalling) {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 600, 0, false, false, true));
                player.sendMessage(Text.literal("§a[缓降] §f已激活 (30秒)"), true);
                hasGivenSlowFalling = true;
            }
            return;
        }

        // 重置缓降标记
        if (hasGivenSlowFalling) {
            hasGivenSlowFalling = false;
        }

        // ===== 手动飞行模式（分数 < 90）=====
        if (isFloating) {
            // 每 tick 重置摔落距离
            player.fallDistance = 0;

            // 落地检测
            if (player.isOnGround()) {
                // 落地前彻底清零
                player.setVelocity(player.getVelocity().x, 0, player.getVelocity().z);
                player.fallDistance = 0;
                deactivateFloating(player);
                player.sendMessage(Text.literal("§c[漂浮] §f已落地解除"), true);
            }
        }
    }

    public static void onPlayerJump(PlayerEntity player) {
        if (player.isCreative() || player.isSpectator()) return;
        if (hasFullWitchTag(player)) return;

        int score = getMonvhuaScore(player);
        if (score >= FULL_WITCH_SCORE) return;

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastJumpTime < DOUBLE_JUMP_INTERVAL) {
            if (isFloating) {
                deactivateFloating(player);
            } else {
                activateFloating(player);
            }
            lastJumpTime = 0;
        } else {
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

        player.sendMessage(Text.literal("§c[漂浮] §f已关闭"), true);
    }

    public static boolean isFloating() {
        return isFloating;
    }
}