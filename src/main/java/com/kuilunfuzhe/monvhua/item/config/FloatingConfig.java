package com.kuilunfuzhe.monvhua.item.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class FloatingConfig {
    private static final int STAGES = 7;
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("floating.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final double[] DEFAULT_DRAIN = {30.0, 24.0, 18.0, 12.0, 6.0, 4.0, 1.5};
    private static final float[] DEFAULT_SPEED = {0.0025f, 0.005f, 0.0075f, 0.010f, 0.0125f, 0.015f, 0.0175f};
    private static FloatingConfig instance;

    public double maxEnergy = 100.0;
    public StageConfig[] stages = new StageConfig[STAGES];

    public static class StageConfig {
        public String name = "";
        public double energyDrainPerSecond = 10.0;
        public float flightSpeed = 0.005f;
        public double energyRegenPerSecond = 10.0;
    }

    public FloatingConfig() {
        for (int i = 0; i < STAGES; i++) {
            stages[i] = new StageConfig();
        }
    }

    public static FloatingConfig getInstance() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    public static void setInstance(FloatingConfig config) {
        instance = sanitize(config);
        instance.save();
    }

    public static void syncInstance(FloatingConfig config) {
        instance = sanitize(config);
    }

    private static FloatingConfig load() {
        if (Files.isRegularFile(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
                return sanitize(GSON.fromJson(reader, FloatingConfig.class));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        FloatingConfig config = createDefault();
        config.save();
        return config;
    }

    private static FloatingConfig sanitize(FloatingConfig config) {
        if (config == null) {
            return createDefault();
        }
        if (config.maxEnergy <= 0) {
            config.maxEnergy = 100.0;
        }
        if (config.stages == null || config.stages.length != STAGES) {
            StageConfig[] oldStages = config.stages;
            config.stages = new StageConfig[STAGES];
            for (int i = 0; i < STAGES; i++) {
                config.stages[i] = oldStages != null && i < oldStages.length && oldStages[i] != null
                        ? oldStages[i]
                        : defaultStage(i + 1);
            }
        }
        for (int i = 0; i < STAGES; i++) {
            if (config.stages[i] == null) {
                config.stages[i] = defaultStage(i + 1);
            }
            if (config.stages[i].name == null || config.stages[i].name.isBlank()) {
                config.stages[i].name = "Stage " + (i + 1);
            }
            config.stages[i].energyDrainPerSecond = Math.max(0.0, config.stages[i].energyDrainPerSecond);
            config.stages[i].flightSpeed = Math.max(0.0f, config.stages[i].flightSpeed);
            config.stages[i].energyRegenPerSecond = Math.max(0.0, config.stages[i].energyRegenPerSecond);
        }
        return config;
    }

    private static FloatingConfig createDefault() {
        FloatingConfig config = new FloatingConfig();
        for (int stage = 1; stage <= STAGES; stage++) {
            config.stages[stage - 1] = defaultStage(stage);
        }
        return config;
    }

    private static StageConfig defaultStage(int stage) {
        StageConfig config = new StageConfig();
        config.name = "Stage " + stage;
        config.energyDrainPerSecond = DEFAULT_DRAIN[stage - 1];
        config.flightSpeed = DEFAULT_SPEED[stage - 1];
        config.energyRegenPerSecond = 10.0 + stage * 2.0;
        return config;
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static FloatingConfig fromJson(String json) {
        return sanitize(GSON.fromJson(json, FloatingConfig.class));
    }

    private StageConfig stage(int stage) {
        return stages[Math.clamp(stage, 1, STAGES) - 1];
    }

    public double getEnergyDrain(int stage) {
        return stage(stage).energyDrainPerSecond;
    }

    public float getFlightSpeed(int stage) {
        return stage(stage).flightSpeed;
    }

    public double getEnergyRegen(int stage) {
        return stage(stage).energyRegenPerSecond;
    }

    public void setEnergyDrain(int stage, double value) {
        stage(stage).energyDrainPerSecond = Math.max(0.0, value);
    }

    public void setFlightSpeed(int stage, float value) {
        stage(stage).flightSpeed = Math.max(0.0f, value);
    }

    public void setEnergyRegen(int stage, double value) {
        stage(stage).energyRegenPerSecond = Math.max(0.0, value);
    }
}
