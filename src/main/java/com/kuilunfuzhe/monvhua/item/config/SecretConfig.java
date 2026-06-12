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
 * 窃密魔法配置，独立于穿墙配置。由指令管理，不在 GUI 中显示。
 */
public class SecretConfig {
    private static final int STAGES = 7;
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("secret.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static SecretConfig instance;

    public StageConfig[] stages = new StageConfig[STAGES];

    public static class StageConfig {
        /** 窃密作用范围（格） */
        public int range = 10;
        /** 窃密触发概率 (0.0–1.0) */
        public double probability = 0.5D;
    }

    public SecretConfig() {
        stages = new StageConfig[STAGES];
        for (int i = 0; i < STAGES; i++) {
            stages[i] = new StageConfig();
        }
    }

    public static SecretConfig getInstance() {
        if (instance == null) instance = load();
        return instance;
    }

    public static void setInstance(SecretConfig config) {
        instance = normalize(config);
        instance.save();
    }

    private static SecretConfig load() {
        if (CONFIG_PATH.toFile().exists()) {
            try (Reader reader = new FileReader(CONFIG_PATH.toFile())) {
                return normalize(GSON.fromJson(reader, SecretConfig.class));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        SecretConfig config = createDefault();
        config.save();
        return config;
    }

    private static SecretConfig normalize(SecretConfig config) {
        if (config == null) return createDefault();
        if (config.stages == null || config.stages.length != STAGES) {
            SecretConfig normalized = createDefault();
            if (config.stages != null) {
                int len = Math.min(config.stages.length, STAGES);
                System.arraycopy(config.stages, 0, normalized.stages, 0, len);
            }
            config = normalized;
        }
        for (int i = 0; i < STAGES; i++) {
            if (config.stages[i] == null) config.stages[i] = new StageConfig();
            config.stages[i].probability = Math.max(0.0D, Math.min(1.0D, config.stages[i].probability));
            config.stages[i].range = Math.max(-1, config.stages[i].range);
        }
        return config;
    }

    private static SecretConfig createDefault() {
        SecretConfig config = new SecretConfig();
        int[] ranges = {8, 16, 32, 64, 128, 256, -1};
        double[] probs = {0.25D, 0.33D, 0.50D, 0.66D, 0.75D, 0.80D, 1.00D};
        for (int i = 0; i < STAGES; i++) {
            config.stages[i].range = ranges[i];
            config.stages[i].probability = probs[i];
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

    public String toJson() { return GSON.toJson(this); }
    public static SecretConfig fromJson(String json) { return normalize(GSON.fromJson(json, SecretConfig.class)); }

    public int getRange(int stage) { return stages[stage - 1].range; }
    public double getProbability(int stage) { return stages[stage - 1].probability; }
    public void setRange(int stage, int val) { stages[stage - 1].range = val; save(); }
    public void setProbability(int stage, double val) { stages[stage - 1].probability = Math.max(0.0D, Math.min(1.0D, val)); save(); }
}