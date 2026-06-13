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

public class PlantMagicConfig {
    private static final int STAGES = 7;
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("plant_magic.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static PlantMagicConfig instance;

    public StageConfig[] stages = new StageConfig[STAGES];

    public static class StageConfig {
        public String name = "";
        public double leafMoveSpeed = 2.0D;
        public double shellCoverageDegrees = 120.0D;
        public int cooldownSeconds = 10;
    }

    public PlantMagicConfig() {
        for (int i = 0; i < STAGES; i++) {
            stages[i] = defaultStage(i + 1);
        }
    }

    public static PlantMagicConfig getInstance() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    public static void setInstance(PlantMagicConfig config) {
        instance = sanitize(config);
        instance.save();
    }

    public static void syncInstance(PlantMagicConfig config) {
        instance = sanitize(config);
    }

    private static PlantMagicConfig load() {
        if (Files.isRegularFile(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
                return sanitize(GSON.fromJson(reader, PlantMagicConfig.class));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        PlantMagicConfig config = createDefault();
        config.save();
        return config;
    }

    private static PlantMagicConfig sanitize(PlantMagicConfig config) {
        if (config == null) {
            return createDefault();
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
            config.stages[i].leafMoveSpeed = Math.max(0.05D, config.stages[i].leafMoveSpeed);
            config.stages[i].shellCoverageDegrees = Math.clamp(config.stages[i].shellCoverageDegrees, 1.0D, 360.0D);
            config.stages[i].cooldownSeconds = Math.max(0, config.stages[i].cooldownSeconds);
        }
        return config;
    }

    private static PlantMagicConfig createDefault() {
        PlantMagicConfig config = new PlantMagicConfig();
        for (int stage = 1; stage <= STAGES; stage++) {
            config.stages[stage - 1] = defaultStage(stage);
        }
        return config;
    }

    private static StageConfig defaultStage(int stage) {
        StageConfig config = new StageConfig();
        config.name = "Stage " + stage;
        config.leafMoveSpeed = 2.0D;
        config.shellCoverageDegrees = 120.0D;
        config.cooldownSeconds = 10;
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

    public static PlantMagicConfig fromJson(String json) {
        return sanitize(GSON.fromJson(json, PlantMagicConfig.class));
    }

    private StageConfig stage(int stage) {
        return stages[Math.clamp(stage, 1, STAGES) - 1];
    }

    public double getLeafMoveSpeed(int stage) {
        return stage(stage).leafMoveSpeed;
    }

    public int getMoveIntervalTicks(int stage) {
        return Math.max(1, (int) Math.round(20.0D / getLeafMoveSpeed(stage)));
    }

    public double getShellCoverageDegrees(int stage) {
        return stage(stage).shellCoverageDegrees;
    }

    public double getShellCoverageDot(int stage) {
        double halfAngle = Math.toRadians(getShellCoverageDegrees(stage) / 2.0D);
        return Math.cos(halfAngle);
    }

    public int getCooldownSeconds(int stage) {
        return stage(stage).cooldownSeconds;
    }

    public int getCooldownTicks(int stage) {
        return getCooldownSeconds(stage) * 20;
    }

    public void setLeafMoveSpeed(int stage, double value) {
        stage(stage).leafMoveSpeed = Math.max(0.05D, value);
    }

    public void setShellCoverageDegrees(int stage, double value) {
        stage(stage).shellCoverageDegrees = Math.clamp(value, 1.0D, 360.0D);
    }

    public void setCooldownSeconds(int stage, int value) {
        stage(stage).cooldownSeconds = Math.max(0, value);
    }
}
