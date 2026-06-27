package com.kuilunfuzhe.monvhua.config;

import java.util.List;
import java.util.Random;

public enum FantasyStage {
    NONE(0, 44, "无", null, 0, new int[]{0, 0}),
    MODERATE(45, 59, "中度", "§7", 1, new int[]{50, 70}),
    HIGH(60, 69, "高度", "§8", 2, new int[]{40, 60}),
    SEVERE(70, 79, "重度", "§0", 3, new int[]{30, 50}),
    PRE_WITCH(80, 89, "准魔女", "§4", 4, new int[]{20, 40}),
    WITCH(90, 100, "魔女", "§5", 5, new int[]{20, 40});

    public final int minScore;
    public final int maxScore;
    public final String displayName;
    public final String colorCode;
    public final int shakeIntensity;
    public final int[] burstInterval;

    private static final Random RANDOM = new Random();

    FantasyStage(int minScore, int maxScore, String displayName, String colorCode, int shakeIntensity, int[] burstInterval) {
        this.minScore = minScore;
        this.maxScore = maxScore;
        this.displayName = displayName;
        this.colorCode = colorCode;
        this.shakeIntensity = shakeIntensity;
        this.burstInterval = burstInterval;
    }

    public static FantasyStage fromScore(int score) {
        for (FantasyStage stage : values()) {
            if (score >= stage.minScore && score <= stage.maxScore) {
                return stage;
            }
        }
        return NONE;
    }

    public boolean isActive() {
        return this != NONE;
    }

    public String getRandomColor() {
        if (this == WITCH) {
            // 魔女：紫、暗红、黑 随机交替
            int choice = RANDOM.nextInt(3);
            if (choice == 0) return "§5";  // 紫色
            if (choice == 1) return "§4";  // 暗红色
            return "§0";                   // 黑色
        }
        return colorCode;
    }

    public List<String> getEdgeTexts() {
        return FantasyConfig.EDGE_TEXT_POOL.getOrDefault(displayName, List.of());
    }

    public List<List<String>> getBurstTexts() {
        return FantasyConfig.BURST_TEXT_POOL.getOrDefault(this, List.of());
    }

    public int getBurstIntervalMin() {
        return burstInterval != null ? burstInterval[0] : 60;
    }

    public int getBurstIntervalMax() {
        return burstInterval != null ? burstInterval[1] : 80;
    }

    public static int getShakeIntensity(int score) {
        FantasyStage stage = fromScore(score);
        return stage.isActive() ? stage.shakeIntensity : 0;
    }
}