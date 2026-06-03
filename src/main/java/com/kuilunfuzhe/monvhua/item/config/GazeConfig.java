package com.kuilunfuzhe.monvhua.item.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Path;

/**
 * 凝视诱导配置文件
 * 管理凝视诱导功能的阶段参数（能耗、恢复速度、半径、最大标记数），支持 JSON 持久化读写
 */
public class GazeConfig {
    /** 凝视诱导的阶段总数 */
    private static final int STAGES = 7;
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("gazeguidance.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static GazeConfig instance;

    /** 各阶段的配置数组（索引 0 对应阶段 1） */
    public StageConfig[] stages = new StageConfig[STAGES];
    /** 凝视诱导最大能量值 */
    public double maxEnergy = 100.0;

    /**
     * 单阶段配置项
     */
    public static class StageConfig {
        /** 阶段显示名称 */
        public String name = "";
        /** 每个标记的能量消耗 */
        public double energyDrainPerMark = 1.0;
        /** 每秒能量恢复量 */
        public double energyRegenPerSecond = 2.0;
        /** 效果半径（格） */
        public double radius = 5.0;
        /** 最大可标记数量 */
        public int maxMarks = 3;
    }

    /** 构造时预填充 stages 数组，避免后续 null 检查 */
    public GazeConfig() {
        stages = new StageConfig[STAGES];
        for (int i = 0; i < STAGES; i++) {
            stages[i] = new StageConfig();
        }
    }

    /**
     * @return 单例配置实例（首次调用时从文件加载或创建默认值）
     */
    public static GazeConfig getInstance() {
        if (instance == null) instance = load();
        return instance;
    }

    /**
     * 替换当前配置实例并立即保存到文件
     * @param config 新配置对象
     */
    public static void setInstance(GazeConfig config) {
        instance = config;
        instance.save();
    }

    private static GazeConfig load() {
        if (CONFIG_PATH.toFile().exists()) {
            try (Reader reader = new FileReader(CONFIG_PATH.toFile())) {
                GazeConfig config = GSON.fromJson(reader, GazeConfig.class);
                if (config.stages == null || config.stages.length != STAGES) return createDefault();
                return config;
            } catch (IOException e) { e.printStackTrace(); }
        }
        return createDefault();
    }

    /**
     * 创建带默认值的配置（首次运行或配置文件损坏时使用）
     * 默认值按阶段递增：能耗 = 阶段数，恢复 = 2+阶段数，半径 = 5/7/9/...，标记上限 = 3/3/6/6/15/15/15
     */
    private static GazeConfig createDefault() {
        GazeConfig config = new GazeConfig();
        for (int i = 0; i < STAGES; i++) {
            int stage = i + 1;
            config.stages[i] = new StageConfig();
            config.stages[i].name = "阶段 " + stage;
            config.stages[i].energyDrainPerMark = stage;
            config.stages[i].energyRegenPerSecond = 2 + stage;
            config.stages[i].radius = 5 + (stage - 1) * 2.0; // 阶段1=5, 阶段7=17
            config.stages[i].maxMarks = stage <= 2 ? 3 : (stage <= 4 ? 6 : 15); // 分三档递增
        }
        return config;
    }

    public void save() {
        try (Writer writer = new FileWriter(CONFIG_PATH.toFile())) {
            GSON.toJson(this, writer);
        } catch (IOException e) { e.printStackTrace(); }
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static GazeConfig fromJson(String json) {
        return GSON.fromJson(json, GazeConfig.class);
    }

    // ==================== 便捷 getter/setter 方法 ====================
    // 参数 stage 为 1-based（阶段 1-7），内部转为 0-based 索引

    /** @param stage 阶段编号（1-7） */
    public double getEnergyDrain(int stage) { return stages[stage-1].energyDrainPerMark; }
    public double getEnergyRegen(int stage) { return stages[stage-1].energyRegenPerSecond; }
    public double getRadius(int stage) { return stages[stage-1].radius; }
    public int getMaxMarks(int stage) { return stages[stage-1].maxMarks; }

    public void setEnergyDrain(int stage, double val) { stages[stage-1].energyDrainPerMark = val; save(); }
    public void setEnergyRegen(int stage, double val) { stages[stage-1].energyRegenPerSecond = val; save(); }
    public void setRadius(int stage, double val) { stages[stage-1].radius = val; save(); }
    public void setMaxMarks(int stage, int val) { stages[stage-1].maxMarks = val; save(); }
}