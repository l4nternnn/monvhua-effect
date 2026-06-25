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

public class GravityConfig {
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("gravity.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int STAGE_COUNT = 7;
    private static final int[] DEFAULT_MAX_PICK_BLOCKS = {0, 8, 12, 18, 24, 32, 48, 64};
    private static final double[] DEFAULT_MAX_PICK_HARDNESS = {0.0D, 1.0D, 1.5D, 2.0D, 3.0D, 5.0D, 10.0D, 50.0D};
    private static final double DEFAULT_DAMAGE_KILOJOULES_PER_HALF_HEART = 3.0D;
    private static GravityConfig instance;

    public int forceDurationSeconds = 10;
    public double damageKilojoulesPerHalfHeart = DEFAULT_DAMAGE_KILOJOULES_PER_HALF_HEART;
    public int[] maxPickBlocksByStage = DEFAULT_MAX_PICK_BLOCKS.clone();
    public double[] maxPickHardnessByStage = DEFAULT_MAX_PICK_HARDNESS.clone();

    public static GravityConfig getInstance() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    public static void setInstance(GravityConfig config) {
        instance = sanitize(config);
        instance.save();
    }

    public static void syncInstance(GravityConfig config) {
        instance = sanitize(config);
    }

    private static GravityConfig load() {
        if (Files.isRegularFile(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
                return sanitize(GSON.fromJson(reader, GravityConfig.class));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        GravityConfig config = new GravityConfig();
        config.save();
        return config;
    }

    private static GravityConfig sanitize(GravityConfig config) {
        if (config == null) {
            config = new GravityConfig();
        }
        config.forceDurationSeconds = Math.clamp(config.forceDurationSeconds, 1, 600);
        config.damageKilojoulesPerHalfHeart = Math.clamp(config.damageKilojoulesPerHalfHeart, 0.1D, 10000.0D);
        config.maxPickBlocksByStage = sanitizeIntStages(config.maxPickBlocksByStage, DEFAULT_MAX_PICK_BLOCKS, 1, 512);
        config.maxPickHardnessByStage = sanitizeDoubleStages(config.maxPickHardnessByStage, DEFAULT_MAX_PICK_HARDNESS, 0.0D, 100.0D);
        return config;
    }

    private static int[] sanitizeIntStages(int[] values, int[] defaults, int min, int max) {
        int[] sanitized = defaults.clone();
        if (values != null) {
            for (int i = 1; i <= STAGE_COUNT && i < values.length; i++) {
                sanitized[i] = Math.clamp(values[i], min, max);
            }
        }
        return sanitized;
    }

    private static double[] sanitizeDoubleStages(double[] values, double[] defaults, double min, double max) {
        double[] sanitized = defaults.clone();
        if (values != null) {
            for (int i = 1; i <= STAGE_COUNT && i < values.length; i++) {
                sanitized[i] = Math.clamp(values[i], min, max);
            }
        }
        return sanitized;
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

    public static GravityConfig fromJson(String json) {
        return sanitize(GSON.fromJson(json, GravityConfig.class));
    }

    public int getForceDurationTicks() {
        return forceDurationSeconds * 20;
    }

    public int getMaxPickBlocks(int stage) {
        return maxPickBlocksByStage[stageIndex(stage)];
    }

    public void setMaxPickBlocks(int stage, int value) {
        maxPickBlocksByStage[stageIndex(stage)] = Math.clamp(value, 1, 512);
    }

    public double getMaxPickHardness(int stage) {
        return maxPickHardnessByStage[stageIndex(stage)];
    }

    public void setMaxPickHardness(int stage, double value) {
        maxPickHardnessByStage[stageIndex(stage)] = Math.clamp(value, 0.0D, 100.0D);
    }

    private static int stageIndex(int stage) {
        return Math.clamp(stage, 1, STAGE_COUNT);
    }
}
