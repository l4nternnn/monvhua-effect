package com.kuilunfuzhe.monvhua.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 千里眼全局配置管理器。
 * 负责阶段配置的JSON持久化、每日观看次数与鹦鹉放置限额管理、
 * 以及跨天自动重置。
 */
public class GlobalConfigManager {
    /** 阶段总数（1~7），阶段号越大对应的分数区间越高 */
    private static final int STAGES = 7;
    /** 阶段号 → 阶段配置 */
    private final Map<Integer, StageConfig> configs = new HashMap<>();
    /** 玩家UUID → 当日各阶段观看（标记）次数 */
    private final Map<UUID, DailyUsage> dailyUsage = new ConcurrentHashMap<>();
    /** 玩家UUID → 当日各阶段鹦鹉放置次数 */
    private final Map<UUID, DailyParrotUsage> parrotDailyUsage = new ConcurrentHashMap<>();
    /** 玩家UUID → 当前活跃鹦鹉数量（用于同时存在数上限检查） */
    private final Map<UUID, Integer> activeParrots = new ConcurrentHashMap<>();
    /** 上次重置的日期（天级时间戳），用于检测跨天 */
    private long lastResetDay = 0;

    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("clairvoyance_global.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * 阶段配置记录。
     * @param dailyLimit 每日标记次数上限
     * @param maxMarks 单次可标记的最大物品数
     * @param minScore 该阶段最低分数阈值
     * @param maxScore 该阶段最高分数阈值
     * @param watchRequiredTicks 观察所需持续tick数
     * @param parrotDailyLimit 每日鹦鹉放置次数上限
     * @param maxActiveParrots 同时存在的最大鹦鹉数量
     */
    public record StageConfig(int dailyLimit, int maxMarks, int minScore, int maxScore, int watchRequiredTicks, int markExpireSeconds,
                              int parrotDailyLimit, int maxActiveParrots,
                              double uiDrainRate, double watchDrainRate, double regenRate) {}


    /** 每日标记使用量，下标为阶段号 */
    private static class DailyUsage {
        int[] used = new int[STAGES + 1];
    }
    /** 每日鹦鹉放置使用量，下标为阶段号 */
    private static class DailyParrotUsage {
        int[] used = new int[STAGES + 1];
    }

    /** 构造时自动加载配置文件 */
    public GlobalConfigManager() {
        load();
    }

    /**
     * 从JSON文件加载配置；若文件不存在或读取失败，则填充默认值并保存。
     */
    private void load() {
        if (CONFIG_PATH.toFile().exists()) {
            try (Reader reader = new FileReader(CONFIG_PATH.toFile())) {
                ConfigData data = GSON.fromJson(reader, ConfigData.class);
                for (int i = 1; i <= STAGES; i++) {
                    StageConfig cfg = data.configs.get(i);
                    configs.put(i, normalizeConfig(cfg, getDefaultConfig(i)));
                }
                return;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        for (int i = 1; i <= STAGES; i++) configs.put(i, getDefaultConfig(i));
        save();
    }

    /** 将当前阶段配置持久化写入JSON文件 */
    public void save() {
        ConfigData data = new ConfigData();
        data.configs = new HashMap<>(configs);
        try (Writer writer = new FileWriter(CONFIG_PATH.toFile())) {
            GSON.toJson(data, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** 各阶段默认配置，阶段越高观察tick越少、限额越宽松 */
    private StageConfig getDefaultConfig(int stage) {
        return switch (stage) {
            case 1 -> new StageConfig(10, 3, 0, 9, 40, 20, 2, 2, 1.0D, 8.0D, 2.0D);
            case 2 -> new StageConfig(10, 4, 10, 24, 40, 20, 3, 2, 1.0D, 8.0D, 2.5D);
            case 3 -> new StageConfig(8, 5, 25, 44, 40, 20, 5, 2, 1.2D, 9.0D, 3.0D);
            case 4 -> new StageConfig(8, 6, 45, 59, 40, 20, 7, 2, 1.2D, 9.0D, 3.5D);
            case 5 -> new StageConfig(6, 7, 60, 69, 40, 20, 8, 2, 1.5D, 10.0D, 4.0D);
            case 6 -> new StageConfig(6, 8, 70, 79, 40, 20, 9, 2, 1.5D, 10.0D, 4.5D);
            case 7 -> new StageConfig(5, 10, 80, 100, 40, 20, 10, 2, 2.0D, 12.0D, 5.0D);
            default -> new StageConfig(10, 5, 0, 100, 40, 20, 12, 2, 1.0D, 8.0D, 2.0D);
        };
    }

    private StageConfig normalizeConfig(StageConfig cfg, StageConfig fallback) {
        if (cfg == null) {
            return fallback;
        }
        return new StageConfig(
                cfg.dailyLimit(),
                cfg.maxMarks(),
                cfg.minScore(),
                cfg.maxScore(),
                cfg.watchRequiredTicks() > 0 ? cfg.watchRequiredTicks() : fallback.watchRequiredTicks(),
                cfg.markExpireSeconds() > 0 ? cfg.markExpireSeconds() : fallback.markExpireSeconds(),
                cfg.parrotDailyLimit(),
                cfg.maxActiveParrots() > 0 ? cfg.maxActiveParrots() : fallback.maxActiveParrots(),
                cfg.uiDrainRate() > 0.0D ? cfg.uiDrainRate() : fallback.uiDrainRate(),
                cfg.watchDrainRate() > 0.0D ? cfg.watchDrainRate() : fallback.watchDrainRate(),
                cfg.regenRate() > 0.0D ? cfg.regenRate() : fallback.regenRate()
        );
    }

    /** 获取指定阶段的配置，不存在则返回默认值 */
    public StageConfig getStageConfig(int stage) {
        return normalizeConfig(configs.get(stage), getDefaultConfig(stage));
    }

    /** 更新指定阶段配置并立即保存到文件 */
    public void updateStageConfig(int stage, int dailyLimit, int maxMarks, int minScore, int maxScore, int watchRequiredTicks, int parrotDailyLimit, int maxActiveParrots) {
        StageConfig existing = getStageConfig(stage);
        configs.put(stage, new StageConfig(dailyLimit, maxMarks, minScore, maxScore, watchRequiredTicks, existing.markExpireSeconds(), parrotDailyLimit, maxActiveParrots,
                existing.uiDrainRate(), existing.watchDrainRate(), existing.regenRate()));
        save();
    }

    public void updateStageConfig(int stage, int dailyLimit, int maxMarks, int minScore, int maxScore, int watchRequiredTicks, int markExpireSeconds,
                                  int parrotDailyLimit, int maxActiveParrots) {
        StageConfig existing = getStageConfig(stage);
        configs.put(stage, new StageConfig(dailyLimit, maxMarks, minScore, maxScore, watchRequiredTicks, markExpireSeconds, parrotDailyLimit,
                maxActiveParrots, existing.uiDrainRate(), existing.watchDrainRate(), existing.regenRate()));
        save();
    }

    public void updateStageConfig(int stage, int dailyLimit, int maxMarks, int minScore, int maxScore, int parrotDailyLimit,
                                  double uiDrainRate, double watchDrainRate, double regenRate) {
        StageConfig existing = getStageConfig(stage);
        configs.put(stage, new StageConfig(dailyLimit, maxMarks, minScore, maxScore, existing.watchRequiredTicks(), existing.markExpireSeconds(), parrotDailyLimit,
                existing.maxActiveParrots(), uiDrainRate, watchDrainRate, regenRate));
        save();
    }

    public void updateStageConfig(int stage, int dailyLimit, int maxMarks, int minScore, int maxScore, int markExpireSeconds, int parrotDailyLimit,
                                  double uiDrainRate, double watchDrainRate, double regenRate) {
        StageConfig existing = getStageConfig(stage);
        configs.put(stage, new StageConfig(dailyLimit, maxMarks, minScore, maxScore, existing.watchRequiredTicks(), markExpireSeconds, parrotDailyLimit,
                existing.maxActiveParrots(), uiDrainRate, watchDrainRate, regenRate));
        save();
    }

    /**
     * 检查玩家当日是否还能标记（观看），用于canMark命令。
     * @return true 表示该阶段当日还有剩余次数
     */
    public boolean canMark(UUID playerUuid, int stage) {
        checkDailyReset();
        DailyUsage usage = dailyUsage.computeIfAbsent(playerUuid, k -> new DailyUsage());
        return usage.used[stage] < getStageConfig(stage).dailyLimit();
    }

    /** 记录一次标记（观看）操作，增加当日计数 */
    public void recordMark(UUID playerUuid, int stage) {
        checkDailyReset();
        DailyUsage usage = dailyUsage.computeIfAbsent(playerUuid, k -> new DailyUsage());
        usage.used[stage]++;
    }

    // 鹦鹉限制：硬编码（每日最多放置5次，同时最多存在3只）
    private static final int PARROT_DAILY_LIMIT = 5;
    private static final int MAX_ACTIVE_PARROTS = 3;

    // 修改 canPlaceParrot 方法，使用阶段限制
// 修改 canPlaceParrot，使用阶段配置中的最大同时存在数
    /**
     * 检查玩家当日是否还能放置鹦鹉。
     * 同时检查每日次数上限和活跃鹦鹉数量上限（均从阶段配置读取）。
     * @return true 表示可以放置
     */
    public boolean canPlaceParrot(UUID playerUuid, int stage) {
        checkDailyReset();
        DailyParrotUsage usage = parrotDailyUsage.computeIfAbsent(playerUuid, k -> new DailyParrotUsage());
        StageConfig cfg = getStageConfig(stage);
        int dailyLimit = cfg.parrotDailyLimit();
        return usage.used[stage] < dailyLimit;
    }

    /** 导出所有阶段配置为JSON字符串（供前端展示或调试用） */
    public String getConfigJson() {
        Map<String, StageConfig> stageMap = new LinkedHashMap<>();
        for (int i = 1; i <= STAGES; i++) {
            stageMap.put("stage" + i, configs.get(i));
        }
        return GSON.toJson(stageMap);
    }

    /** 兼容方法：更新阶段配置（委托给updateStageConfig） */
    public void updateConfig(int stage, int dailyLimit, int maxMarks, int minScore, int maxScore,
                             int watchRequiredTicks, int parrotDailyLimit, int maxActiveParrots) {
        updateStageConfig(stage, dailyLimit, maxMarks, minScore, maxScore,
                watchRequiredTicks, parrotDailyLimit, maxActiveParrots);
    }

    public void updateConfig(int stage, int dailyLimit, int maxMarks, int minScore, int maxScore,
                             int watchRequiredTicks, int markExpireSeconds, int parrotDailyLimit, int maxActiveParrots) {
        updateStageConfig(stage, dailyLimit, maxMarks, minScore, maxScore,
                watchRequiredTicks, markExpireSeconds, parrotDailyLimit, maxActiveParrots);
    }

    public void updateConfig(int stage, int dailyLimit, int maxMarks, int minScore, int maxScore, int parrotDailyLimit,
                             double uiDrainRate, double watchDrainRate, double regenRate) {
        updateStageConfig(stage, dailyLimit, maxMarks, minScore, maxScore, parrotDailyLimit,
                uiDrainRate, watchDrainRate, regenRate);
    }

    public void updateConfig(int stage, int dailyLimit, int maxMarks, int minScore, int maxScore, int markExpireSeconds, int parrotDailyLimit,
                             double uiDrainRate, double watchDrainRate, double regenRate) {
        updateStageConfig(stage, dailyLimit, maxMarks, minScore, maxScore, markExpireSeconds, parrotDailyLimit,
                uiDrainRate, watchDrainRate, regenRate);
    }

    /** 记录一次鹦鹉放置：增加当日计数和活跃鹦鹉数 */
    public void recordPlaceParrot(UUID playerUuid, int stage) {
        checkDailyReset();
        DailyParrotUsage usage = parrotDailyUsage.computeIfAbsent(playerUuid, k -> new DailyParrotUsage());
        usage.used[stage]++;
        activeParrots.put(playerUuid, activeParrots.getOrDefault(playerUuid, 0) + 1);
    }

    /** 减少玩家的一只活跃鹦鹉计数（鹦鹉被移除或消失时调用） */
    public void removeActiveParrot(UUID playerUuid) {
        activeParrots.computeIfPresent(playerUuid, (k, v) -> v > 0 ? v - 1 : 0);
    }

    public int getActiveParrotCount(UUID playerUuid) {
        return activeParrots.getOrDefault(playerUuid, 0);
    }

    /** 根据分数反查阶段号，从高阶段向低阶段匹配，确保高分优先命中更高阶段 */
    public int getStageByScore(int score) {
        for (int i = STAGES; i >= 1; i--) {
            StageConfig cfg = getStageConfig(i);
            if (score >= cfg.minScore() && score <= cfg.maxScore()) return i;
        }
        return 1; // 兜底返回最低阶段
    }

    /**
     * 跨天重置检查。
     * 将当前毫秒时间戳除以一天的毫秒数（86400000）得到天级时间戳，
     * 与上次记录的天数不同则表示已跨天，清零所有每日计数。
     */
    private void checkDailyReset() {
        long currentDay = System.currentTimeMillis() / 86400000L; // 86400000 = 24 * 60 * 60 * 1000
        if (currentDay != lastResetDay) {
            dailyUsage.clear();
            parrotDailyUsage.clear();
            lastResetDay = currentDay;
        }
    }
}

/** JSON序列化/反序列化用的配置数据容器 */
class ConfigData {
    Map<Integer, GlobalConfigManager.StageConfig> configs;
}
