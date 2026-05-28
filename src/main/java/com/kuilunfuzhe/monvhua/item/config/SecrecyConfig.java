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

public class SecrecyConfig {
    private static final int STAGES = 7;
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("secrecy.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static SecrecyConfig instance;

    public StageConfig[] stages = new StageConfig[STAGES];

    public static class StageConfig {
        public int range = 10;
        public double probability = 0.5D;
        public double speedMultiplier = 0.1D;
        public int vanishDelaySeconds = 5;
    }

    public SecrecyConfig() {
        stages = new StageConfig[STAGES];
        for (int i = 0; i < STAGES; i++) {
            stages[i] = new StageConfig();
        }
    }

    public static SecrecyConfig getInstance() {
        if (instance == null) instance = load();
        return instance;
    }

    public static void setInstance(SecrecyConfig config) {
        instance = normalize(config);
        instance.save();
    }

    private static SecrecyConfig load() {
        if (CONFIG_PATH.toFile().exists()) {
            try (Reader reader = new FileReader(CONFIG_PATH.toFile())) {
                return normalize(GSON.fromJson(reader, SecrecyConfig.class));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        SecrecyConfig config = createDefault();
        config.save();
        return config;
    }

    private static SecrecyConfig normalize(SecrecyConfig config) {
        if (config == null) return createDefault();
        if (config.stages == null || config.stages.length != STAGES) {
            SecrecyConfig normalized = createDefault();
            if (config.stages != null) {
                int len = Math.min(config.stages.length, STAGES);
                System.arraycopy(config.stages, 0, normalized.stages, 0, len);
            }
            config = normalized;
        }
        for (int i = 0; i < STAGES; i++) {
            if (config.stages[i] == null) config.stages[i] = new StageConfig();
            config.stages[i].probability = Math.max(0.0D, Math.min(1.0D, config.stages[i].probability));
            config.stages[i].speedMultiplier = Math.max(0.0D, config.stages[i].speedMultiplier);
            config.stages[i].vanishDelaySeconds = Math.max(-1, config.stages[i].vanishDelaySeconds);
        }
        return config;
    }

    private static SecrecyConfig createDefault() {
        SecrecyConfig config = new SecrecyConfig();

        int[] ranges = {
                4, 8, 12, 18, 24, 32, 40
        };

        double[] probabilities = {
                0.25D, 0.33D, 0.50D, 0.66D, 0.75D, 0.80D, 1.00D
        };

        double[] speedMultipliers = {
                0.05D, 0.05D, 0.05D, 0.05D, 0.05D, 0.05D, 0.05D
        };

        int[] vanishDelaySeconds = {
                4, 4, 3, 3, 2, 2, 1
        };

        for (int i = 0; i < STAGES; i++) {
            config.stages[i] = new StageConfig();
            config.stages[i].range = ranges[i];
            config.stages[i].probability = probabilities[i];
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

    public static SecrecyConfig fromJson(String json) {
        return normalize(GSON.fromJson(json, SecrecyConfig.class));
    }

    public int getRange(int stage) { return stages[stage - 1].range; }
    public double getProbability(int stage) { return stages[stage - 1].probability; }
    public double getSpeedMultiplier(int stage) { return stages[stage - 1].speedMultiplier; }
    public int getVanishDelaySeconds(int stage) { return stages[stage - 1].vanishDelaySeconds; }

    public void setRange(int stage, int val) { stages[stage - 1].range = val; save(); }
    public void setProbability(int stage, double val) { stages[stage - 1].probability = Math.max(0.0D, Math.min(1.0D, val)); save(); }
    public void setSpeedMultiplier(int stage, double val) { stages[stage - 1].speedMultiplier = Math.max(0.0D, val); save(); }
    public void setVanishDelaySeconds(int stage, int val) { stages[stage - 1].vanishDelaySeconds = Math.max(-1, val); save(); }
}
