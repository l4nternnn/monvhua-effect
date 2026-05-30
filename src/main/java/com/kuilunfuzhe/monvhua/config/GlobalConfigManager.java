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
    public record StageConfig(int dailyLimit, int maxMarks, int minScore, int maxScore, int watchRequiredTicks, int parrotDailyLimit, int maxActiveParrots) {}


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
                    configs.put(i, cfg != null ? cfg : getDefaultConfig(i));
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
            case 1 -> new StageConfig(10, 3, 0, 5, 40,2,2);
            case 2 -> new StageConfig(10, 4, 6, 20, 40,3,2);
            case 3 -> new StageConfig(8, 5, 21, 40, 40,5,2);
            case 4 -> new StageConfig(8, 6, 41, 60, 40,7,2);
            case 5 -> new StageConfig(6, 7, 61, 70, 40,8,2);
            case 6 -> new StageConfig(6, 8, 71, 80, 40,9,2);
            case 7 -> new StageConfig(5, 10, 81, 100, 40,10,2);
            default -> new StageConfig(10, 5, 0, 100, 40,12,2);
        };
    }

    /** 获取指定阶段的配置，不存在则返回默认值 */
    public StageConfig getStageConfig(int stage) {
        return configs.getOrDefault(stage, getDefaultConfig(stage));
    }

    /** 更新指定阶段配置并立即保存到文件 */
    public void updateStageConfig(int stage, int dailyLimit, int maxMarks, int minScore, int maxScore, int watchRequiredTicks, int parrotDailyLimit, int maxActiveParrots) {
        configs.put(stage, new StageConfig(dailyLimit, maxMarks, minScore, maxScore, watchRequiredTicks,parrotDailyLimit,maxActiveParrots));
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
        int maxActive = cfg.maxActiveParrots();
        int currentActive = activeParrots.getOrDefault(playerUuid, 0);
        return usage.used[stage] < dailyLimit && currentActive < maxActive;
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