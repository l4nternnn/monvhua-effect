package com.kuilunfuzhe.monvhua.features.fantasy;

import com.kuilunfuzhe.monvhua.config.FantasyConfig;
import com.kuilunfuzhe.monvhua.config.FantasyStage;
import com.kuilunfuzhe.monvhua.network.fantasy.FantasyS2CPacket;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.*;

public class FantasyManager {

    private static final Random RANDOM = new Random();
    private static final Map<UUID, PlayerFantasyState> playerStates = new HashMap<>();

    private static final int SENTENCE_DURATION_MIN = FantasyConfig.SENTENCE_DURATION_MIN;
    private static final int SENTENCE_DURATION_MAX = FantasyConfig.SENTENCE_DURATION_MAX;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                tick(player);
            }
        });
    }

    /**
     * 根据阶段返回常驻字幕间隔（tick），阶段越高间隔越短
     */
    private static int getEdgeInterval(FantasyStage stage) {
        switch (stage) {
            case MODERATE:
                return 120 + RANDOM.nextInt(80);   // 中度：2-3秒
            case HIGH:
                return 80 + RANDOM.nextInt(60);    // 高度：1.5-2.5秒
            case SEVERE:
                return 50 + RANDOM.nextInt(40);    // 重度：1-1.5秒
            case PRE_WITCH:
                return 30 + RANDOM.nextInt(30);    // 准魔女：0.5-1秒
            case WITCH:
                return 8 + RANDOM.nextInt(12);     // 魔女：0.4-1秒（大幅增加频率）
            default:
                return 100;
        }
    }

    /**
     * 根据阶段返回常驻乱码概率（0-1）
     */
    private static float getEdgeGlitchProbability(FantasyStage stage) {
        switch (stage) {
            case MODERATE:
                return 0.1f;   // 10%
            case HIGH:
                return 0.2f;   // 20%
            case SEVERE:
                return 0.3f;   // 30%
            case PRE_WITCH:
                return 0.5f;   // 50%
            case WITCH:
                return 0.8f;   // 80%（魔女阶段几乎每条常驻都带乱码）
            default:
                return 0.0f;
        }
    }

    private static void tick(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        int score = getMonvhuaScore(player);
        FantasyStage stage = FantasyStage.fromScore(score);

        PlayerFantasyState state = playerStates.computeIfAbsent(uuid, k -> new PlayerFantasyState());

        if (!stage.isActive()) {
            state.hasActiveBurst = false;
            state.burstLines = null;
            state.burstTextIndex = 0;
            return;
        }

        // 爆发期
        if (!state.hasActiveBurst) {
            long currentTick = getServerTick(player);
            if (currentTick >= state.nextBurstTick) {
                startBurst(player, stage, state);
            }
        } else {
            tickBurst(player, stage, state);
        }

        // 常驻字幕（频率随阶段提高）
        state.edgeCooldown--;
        if (state.edgeCooldown <= 0) {
            sendEdgeText(player, stage);
            // 根据概率发送常驻乱码
            if (RANDOM.nextFloat() < getEdgeGlitchProbability(stage)) {
                sendEdgeGlitch(player, stage);
            }
            state.edgeCooldown = getEdgeInterval(stage);
        }
    }

    private static void startBurst(ServerPlayerEntity player, FantasyStage stage, PlayerFantasyState state) {
        List<List<String>> burstPool = stage.getBurstTexts();

        System.out.println("§e[调试] 阶段: " + stage.displayName + ", 爆发池大小: " + burstPool.size());
        if (burstPool.isEmpty()) {
            System.out.println("§e[调试] 爆发池为空，跳过爆发");
            state.nextBurstTick = getServerTick(player) + ticksFromSeconds(stage.getBurstIntervalMin());
            return;
        }

        int index = RANDOM.nextInt(burstPool.size());
        List<String> selected = burstPool.get(index);

        if (burstPool.size() > 1 && selected.equals(state.lastBurstText)) {
            int newIndex = RANDOM.nextInt(burstPool.size());
            while (newIndex == index) {
                newIndex = RANDOM.nextInt(burstPool.size());
            }
            selected = burstPool.get(newIndex);
        }

        state.lastBurstText = selected;
        state.hasActiveBurst = true;
        state.burstLines = new ArrayList<>(selected);
        state.burstTextIndex = 0;
        state.nextSentenceTick = getServerTick(player) + 5;

        int min = stage.getBurstIntervalMin();
        int max = stage.getBurstIntervalMax();
        int interval = min + RANDOM.nextInt(max - min + 1);
        state.nextBurstTick = getServerTick(player) + ticksFromSeconds(interval);

        // 魔女阶段不施加负面效果
        if (stage != FantasyStage.WITCH) {
            applyStatusEffects(player, stage);
        }
    }

    private static void tickBurst(ServerPlayerEntity player, FantasyStage stage, PlayerFantasyState state) {
        long currentTick = getServerTick(player);
        if (currentTick < state.nextSentenceTick) return;

        if (state.burstLines == null || state.burstTextIndex >= state.burstLines.size()) {
            // 爆发结束时多发几条乱码
            for (int i = 0; i < 5; i++) {
                sendGlitch(player, stage);
            }
            state.hasActiveBurst = false;
            state.burstLines = null;
            state.burstTextIndex = 0;
            return;
        }

        String line = state.burstLines.get(state.burstTextIndex);

        // 基础时长 100-240 tick
        int duration = 100 + RANDOM.nextInt(140);
        // 魔女阶段持续时间翻倍（乱码不翻倍，只在发送时判断）
        if (stage == FantasyStage.WITCH) {
            duration *= 2;
        }
        String color = stage.getRandomColor();

        FantasyS2CPacket packet = new FantasyS2CPacket(
                color + line,
                duration,
                true,
                false,
                color
        );
        ServerPlayNetworking.send(player, packet);

        state.burstTextIndex++;

        // 句子间隔更短（5-20 tick）
        state.nextSentenceTick = currentTick + 5 + RANDOM.nextInt(15);

        // 每条句子后面跟 2-4 条乱码
        int glitchCount = 2 + RANDOM.nextInt(3);
        for (int i = 0; i < glitchCount; i++) {
            sendGlitch(player, stage);
        }
    }

    private static void sendEdgeText(ServerPlayerEntity player, FantasyStage stage) {
        List<String> pool = stage.getEdgeTexts();
        if (pool.isEmpty()) return;

        String text = pool.get(RANDOM.nextInt(pool.size()));
        // 基础持续时间
        int duration = SENTENCE_DURATION_MIN + RANDOM.nextInt(SENTENCE_DURATION_MAX - SENTENCE_DURATION_MIN + 1);
        // 根据阶段应用倍率
        float multiplier = 1.0f;
        switch (stage) {
            case SEVERE:
                multiplier = 1.5f;
                break;
            case PRE_WITCH:
                multiplier = 2.0f;
                break;
            case WITCH:
                multiplier = 3.0f;
                break;
            default:
                multiplier = 1.0f;
                break;
        }
        duration = (int)(duration * multiplier);

        String color = stage.getRandomColor();

        FantasyS2CPacket packet = new FantasyS2CPacket(
                color + text,
                duration,
                false,
                false,
                color
        );
        ServerPlayNetworking.send(player, packet);
    }

    /**
     * 发送常驻乱码（边缘，不爆发）
     */
    private static void sendEdgeGlitch(ServerPlayerEntity player, FantasyStage stage) {
        int length = 8 + RANDOM.nextInt(15);
        StringBuilder glitchText = new StringBuilder("§k");
        for (int i = 0; i < length; i++) {
            glitchText.append((char) ('!' + RANDOM.nextInt(94)));
        }
        String color = stage.getRandomColor();
        FantasyS2CPacket packet = new FantasyS2CPacket(
                color + glitchText.toString(),
                20 + RANDOM.nextInt(30),  // 常驻乱码显示 1-2.5 秒
                false,   // 不是爆发期
                true,
                color
        );
        ServerPlayNetworking.send(player, packet);
    }

    private static void sendGlitch(ServerPlayerEntity player, FantasyStage stage) {
        int length = 12 + RANDOM.nextInt(20);
        StringBuilder glitchText = new StringBuilder("§k");
        for (int i = 0; i < length; i++) {
            glitchText.append((char) ('!' + RANDOM.nextInt(94)));
        }
        String color = stage.getRandomColor();
        FantasyS2CPacket packet = new FantasyS2CPacket(
                color + glitchText.toString(),
                15 + RANDOM.nextInt(30),
                true,
                true,
                color
        );
        ServerPlayNetworking.send(player, packet);
    }

    /**
     * 根据阶段施加负面效果（魔女阶段不施加）
     */
    private static void applyStatusEffects(ServerPlayerEntity player, FantasyStage stage) {
        // 魔女阶段不施加
        if (stage == FantasyStage.WITCH) return;

        if (stage == FantasyStage.HIGH) {
            player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.DARKNESS, 100, 0, false, false, true));
        } else if (stage == FantasyStage.SEVERE || stage == FantasyStage.PRE_WITCH) {
            player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.DARKNESS, 100, 0, false, false, true));
            player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.SLOWNESS, 100, 1, false, false, true));
        }
        // 注意：魔女阶段不施加任何效果
    }

    private static int getMonvhuaScore(ServerPlayerEntity player) {
        // ===== 精神治疗：有 Healed 标签则强制分数为 0 =====
        if (player.getCommandTags().contains("Healed")) {
            return 0;
        }

        var objective = player.getScoreboard().getNullableObjective("monvhua");
        if (objective == null) return 0;
        var score = player.getScoreboard().getScore(player, objective);
        return score == null ? 0 : score.getScore();
    }

    private static long getServerTick(ServerPlayerEntity player) {
        if (player.getWorld() instanceof ServerWorld serverWorld) {
            return serverWorld.getTime();
        }
        return 0;
    }

    private static long ticksFromSeconds(int seconds) {
        return seconds * 20L;
    }

    public static void initialize() {
        register();
    }

    private static class PlayerFantasyState {
        boolean hasActiveBurst = false;
        List<String> burstLines = null;
        int burstTextIndex = 0;
        long nextBurstTick = 20;
        long nextSentenceTick = 0;
        int edgeCooldown = 20;
        List<String> lastBurstText = null;
    }
}