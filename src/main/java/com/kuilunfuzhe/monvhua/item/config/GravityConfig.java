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
    private static final double[] DEFAULT_MAX_FORCE = {0.0D, 3.75D, 3.75D, 3.75D, 3.75D, 3.75D, 3.75D, 3.75D};
    private static final double[] DEFAULT_SELF_FORCE_DRAIN = {0.0D, 18.0D, 16.0D, 14.0D, 12.0D, 10.0D, 8.0D, 6.0D};
    private static final double[] DEFAULT_ENERGY_REGEN = {0.0D, 5.0D, 6.0D, 7.0D, 8.0D, 10.0D, 12.0D, 14.0D};
    private static final double[] DEFAULT_EXTRACT_DRAIN = {0.0D, 8.0D, 7.0D, 6.0D, 5.0D, 4.0D, 3.0D, 2.0D};
    private static final double[] DEFAULT_HOLD_DRAIN = {0.0D, 3.0D, 2.7D, 2.4D, 2.0D, 1.6D, 1.2D, 1.0D};
    private static final int[] DEFAULT_EXTRACT_TICKS = {0, 60, 56, 52, 48, 44, 40, 36};
    private static final double DEFAULT_DAMAGE_KILOJOULES_PER_HALF_HEART = 3.0D;
    private static final float DEFAULT_SURFACE_MOVE_DRAIN_PER_BLOCK = 1.0F;
    private static final int CURRENT_MAX_FORCE_CONFIG_VERSION = 2;
    private static GravityConfig instance;

    public int maxForceConfigVersion = CURRENT_MAX_FORCE_CONFIG_VERSION;
    public int forceDurationSeconds = 10;
    public double damageKilojoulesPerHalfHeart = DEFAULT_DAMAGE_KILOJOULES_PER_HALF_HEART;
    public float surfaceMoveDrainPerBlock = DEFAULT_SURFACE_MOVE_DRAIN_PER_BLOCK;
    public int[] maxPickBlocksByStage = DEFAULT_MAX_PICK_BLOCKS.clone();
    public double[] maxPickHardnessByStage = DEFAULT_MAX_PICK_HARDNESS.clone();
    public double[] maxForceByStage = DEFAULT_MAX_FORCE.clone();
    public double[] selfForceDrainByStage = DEFAULT_SELF_FORCE_DRAIN.clone();
    public double[] energyRegenByStage = DEFAULT_ENERGY_REGEN.clone();
    public double[] blockExtractDrainByStage = DEFAULT_EXTRACT_DRAIN.clone();
    public double[] blockHoldDrainByStage = DEFAULT_HOLD_DRAIN.clone();
    public int[] blockExtractTicksByStage = DEFAULT_EXTRACT_TICKS.clone();

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
        config.surfaceMoveDrainPerBlock = Math.clamp(config.surfaceMoveDrainPerBlock, 0.0F, 1000.0F);
        config.maxPickBlocksByStage = sanitizeIntStages(config.maxPickBlocksByStage, DEFAULT_MAX_PICK_BLOCKS, 1, 512);
        config.maxPickHardnessByStage = sanitizeDoubleStages(config.maxPickHardnessByStage, DEFAULT_MAX_PICK_HARDNESS, 0.0D, 100.0D);
        config.maxForceByStage = sanitizeMaxForceStages(config.maxForceByStage, config.maxForceConfigVersion < CURRENT_MAX_FORCE_CONFIG_VERSION);
        config.maxForceConfigVersion = CURRENT_MAX_FORCE_CONFIG_VERSION;
        config.selfForceDrainByStage = sanitizeDoubleStages(config.selfForceDrainByStage, DEFAULT_SELF_FORCE_DRAIN, 0.0D, 1000.0D);
        config.energyRegenByStage = sanitizeDoubleStages(config.energyRegenByStage, DEFAULT_ENERGY_REGEN, 0.0D, 1000.0D);
        config.blockExtractDrainByStage = sanitizeDoubleStages(config.blockExtractDrainByStage, DEFAULT_EXTRACT_DRAIN, 0.0D, 1000.0D);
        config.blockHoldDrainByStage = sanitizeDoubleStages(config.blockHoldDrainByStage, DEFAULT_HOLD_DRAIN, 0.0D, 1000.0D);
        config.blockExtractTicksByStage = sanitizeIntStages(config.blockExtractTicksByStage, DEFAULT_EXTRACT_TICKS, 1, 20 * 60);
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

    private static double[] sanitizeMaxForceStages(double[] values, boolean migrateInternalGravityValues) {
        double[] sanitized = DEFAULT_MAX_FORCE.clone();
        if (values != null) {
            for (int i = 1; i <= STAGE_COUNT && i < values.length; i++) {
                double value = values[i];
                if (migrateInternalGravityValues && value > 0.0D && value <= 1.0D) {
                    value /= 0.08D;
                }
                sanitized[i] = Math.clamp(value, 0.0D, 100.0D);
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

    public double getMaxForce(int stage) {
        return maxForceByStage[stageIndex(stage)];
    }

    public void setMaxForce(int stage, double value) {
        maxForceByStage[stageIndex(stage)] = Math.clamp(value, 0.0D, 100.0D);
    }

    public double getSelfForceDrain(int stage) {
        return selfForceDrainByStage[stageIndex(stage)];
    }

    public void setSelfForceDrain(int stage, double value) {
        selfForceDrainByStage[stageIndex(stage)] = Math.clamp(value, 0.0D, 1000.0D);
    }

    public double getEnergyRegen(int stage) {
        return energyRegenByStage[stageIndex(stage)];
    }

    public void setEnergyRegen(int stage, double value) {
        energyRegenByStage[stageIndex(stage)] = Math.clamp(value, 0.0D, 1000.0D);
    }

    public float getSurfaceMoveDrainPerBlock() {
        return surfaceMoveDrainPerBlock;
    }

    public void setSurfaceMoveDrainPerBlock(float value) {
        surfaceMoveDrainPerBlock = Math.clamp(value, 0.0F, 1000.0F);
    }

    public double getBlockExtractDrain(int stage) {
        return blockExtractDrainByStage[stageIndex(stage)];
    }

    public void setBlockExtractDrain(int stage, double value) {
        blockExtractDrainByStage[stageIndex(stage)] = Math.clamp(value, 0.0D, 1000.0D);
    }

    public double getBlockHoldDrain(int stage) {
        return blockHoldDrainByStage[stageIndex(stage)];
    }

    public void setBlockHoldDrain(int stage, double value) {
        blockHoldDrainByStage[stageIndex(stage)] = Math.clamp(value, 0.0D, 1000.0D);
    }

    public int getBlockExtractTicks(int stage) {
        return blockExtractTicksByStage[stageIndex(stage)];
    }

    public void setBlockExtractTicks(int stage, int value) {
        blockExtractTicksByStage[stageIndex(stage)] = Math.clamp(value, 1, 20 * 60);
    }

    private static int stageIndex(int stage) {
        return Math.clamp(stage, 1, STAGE_COUNT);
    }
}
