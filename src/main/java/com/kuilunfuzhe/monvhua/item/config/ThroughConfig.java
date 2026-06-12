package com.kuilunfuzhe.monvhua.item.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Path;

/**
 * 隐秘配置文件
 * 管理隐秘功能的阶段参数（作用范围、触发概率、速度倍率、消失延迟），支持 JSON 持久化读写和参数归一化校验
 */
public class ThroughConfig {
    /** 隐秘的阶段总数 */
    private static final int STAGES = 7;
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("through.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ThroughConfig instance;

    /** 各阶段的配置数组（索引 0 对应阶段 1） */
    public StageConfig[] stages = new StageConfig[STAGES];

    /**
     * 单阶段配置项
     */
    public static class StageConfig {
        /** 速度倍率 */
        public double speedMultiplier = 0.1D;
        /** 消失延迟（秒），-1 表示永不消失 */
        public int vanishDelaySeconds = 5;
    }

    /** 构造时预填充 stages 数组，避免后续 null 检查 */
    public ThroughConfig() {
        stages = new StageConfig[STAGES];
        for (int i = 0; i < STAGES; i++) {
            stages[i] = new StageConfig();
        }
    }

    /**
     * @return 单例配置实例（首次调用时从文件加载并归一化）
     */
    public static ThroughConfig getInstance() {
        if (instance == null) instance = load();
        return instance;
    }

    public static void setInstance(ThroughConfig config) {
        instance = normalize(config);
        instance.save();
    }

    private static ThroughConfig load() {
        if (CONFIG_PATH.toFile().exists()) {
            try (Reader reader = new FileReader(CONFIG_PATH.toFile())) {
                return normalize(GSON.fromJson(reader, ThroughConfig.class));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        ThroughConfig config = createDefault();
        config.save();
        return config;
    }

    /**
     * 归一化配置数据：补全缺失的阶段数组、钳位非法值到合法范围
     * @param config 原始配置（可能为 null 或字段残缺）
     * @return 归一化后的安全配置对象
     */
    private static ThroughConfig normalize(ThroughConfig config) {
        if (config == null) return createDefault();
        if (config.stages == null || config.stages.length != STAGES) {
            ThroughConfig normalized = createDefault();
            if (config.stages != null) {
                int len = Math.min(config.stages.length, STAGES);
                System.arraycopy(config.stages, 0, normalized.stages, 0, len);
            }
            config = normalized;
        }
        for (int i = 0; i < STAGES; i++) {
            if (config.stages[i] == null) config.stages[i] = new StageConfig();
            config.stages[i].speedMultiplier = Math.max(0.0D, config.stages[i].speedMultiplier);
            config.stages[i].vanishDelaySeconds = Math.max(-1, config.stages[i].vanishDelaySeconds);
        }
        return config;
    }

    /**
     * 创建带默认值的配置（首次运行或配置文件损坏时使用）
     */
    private static ThroughConfig createDefault() {
        ThroughConfig config = new ThroughConfig();

        // 各阶段作用范围（格），从阶段1到阶段7递增
        int[] ranges = {
                4, 8, 12, 18, 24, 32, 40
        };

        // 各阶段触发概率，高级阶段逐渐逼近 100%
        double[] probabilities = {
                0.25D, 0.33D, 0.50D, 0.66D, 0.75D, 0.80D, 1.00D
        };

        // 各阶段速度倍率，当前所有阶段统一为 0.05
        double[] speedMultipliers = {
                0.05D, 0.05D, 0.05D, 0.05D, 0.05D, 0.05D, 0.05D
        };

        // 各阶段消失延迟（秒），高级阶段消失更快
        int[] vanishDelaySeconds = {
                4, 4, 3, 3, 2, 2, 1
        };

        for (int i = 0; i < STAGES; i++) {
            config.stages[i] = new StageConfig();

            config.stages[i].speedMultiplier = speedMultipliers[i];
            config.stages[i].vanishDelaySeconds = vanishDelaySeconds[i];
        }

        return config;
    }

    public void save() {
        try (Writer writer = new FileWriter(CONFIG_PATH.toFile())) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static ThroughConfig fromJson(String json) {
        return normalize(GSON.fromJson(json, ThroughConfig.class));
    }


    public double getSpeedMultiplier(int stage) { return stages[stage - 1].speedMultiplier; }
    public int getVanishDelaySeconds(int stage) { return stages[stage - 1].vanishDelaySeconds; }


    public void setSpeedMultiplier(int stage, double val) { stages[stage - 1].speedMultiplier = Math.max(0.0D, val); save(); }
    public void setVanishDelaySeconds(int stage, int val) { stages[stage - 1].vanishDelaySeconds = Math.max(-1, val); save(); }
}
