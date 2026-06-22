package com.kuilunfuzhe.monvhua.item.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Path;

public class ImitateConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("monvhua-imitate-config");
    private static final int STAGES = 7;
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("imitate.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ImitateConfig instance;

    public int soundWaveUnlockThreshold = 60;
    public int silenceUnlockThreshold = 70;
    public int areaSelectUnlockThreshold = 30;
    public StageConfig[] stages = new StageConfig[STAGES];

    public static class StageConfig {
        public String name = "";
        public int durationSeconds = 0;
        public int switchCooldownSeconds = 0;
        public int soundWaveCooldownSeconds = 0;
        public double soundWaveRadius = 10.0;
        public int soundWaveEffectDuration = 5;
        public double silenceRadius = 10.0;
        public int silenceDuration = 10;
        public int silenceCooldown = 0;
        public double imitateRadius = 5.0;
    }

    public ImitateConfig() {
        stages = new StageConfig[STAGES];
        for (int i = 0; i < STAGES; i++) {
            stages[i] = new StageConfig();
        }
    }

    public static ImitateConfig getInstance() {
        if (instance == null) instance = load();
        LOGGER.info("获取配置实例: 阶段1切换冷却={}, 阶段1声波冷却={}", 
            instance.stages[0].switchCooldownSeconds, instance.stages[0].soundWaveCooldownSeconds);
        return instance;
    }

    public static void setInstance(ImitateConfig config) {
        instance = normalize(config);
        instance.save();
        LOGGER.info("配置已更新并保存: 阶段1切换冷却={}, 阶段1声波冷却={}", 
            instance.stages[0].switchCooldownSeconds, instance.stages[0].soundWaveCooldownSeconds);
    }

    private static ImitateConfig load() {
        LOGGER.info("尝试从文件加载配置: {}", CONFIG_PATH.toFile().getAbsolutePath());
        if (CONFIG_PATH.toFile().exists()) {
            try (Reader reader = new FileReader(CONFIG_PATH.toFile())) {
                ImitateConfig config = normalize(GSON.fromJson(reader, ImitateConfig.class));
                LOGGER.info("配置已从文件加载: 阶段1切换冷却={}, 阶段1声波冷却={}", 
                    config.stages[0].switchCooldownSeconds, config.stages[0].soundWaveCooldownSeconds);
                return config;
            } catch (IOException e) {
                LOGGER.error("加载配置文件失败: {}", e.getMessage());
                e.printStackTrace();
            }
        }
        LOGGER.info("配置文件不存在，创建默认配置");
        ImitateConfig config = createDefault();
        config.save();
        return config;
    }

    private static ImitateConfig normalize(ImitateConfig config) {
        if (config == null) return createDefault();
        if (config.stages == null || config.stages.length != STAGES) {
            ImitateConfig normalized = createDefault();
            if (config.stages != null) {
                int len = Math.min(config.stages.length, STAGES);
                System.arraycopy(config.stages, 0, normalized.stages, 0, len);
            }
            config = normalized;
        }
        for (int i = 0; i < STAGES; i++) {
            if (config.stages[i] == null) config.stages[i] = new StageConfig();
            config.stages[i].durationSeconds = Math.max(0, config.stages[i].durationSeconds);
            config.stages[i].switchCooldownSeconds = Math.max(0, config.stages[i].switchCooldownSeconds);
            config.stages[i].soundWaveCooldownSeconds = Math.max(0, config.stages[i].soundWaveCooldownSeconds);
            config.stages[i].soundWaveRadius = Math.max(1.0, config.stages[i].soundWaveRadius);
            config.stages[i].soundWaveEffectDuration = Math.max(1, config.stages[i].soundWaveEffectDuration);
            config.stages[i].silenceRadius = Math.max(1.0, config.stages[i].silenceRadius);
            config.stages[i].silenceDuration = Math.max(1, config.stages[i].silenceDuration);
            config.stages[i].silenceCooldown = Math.max(0, config.stages[i].silenceCooldown);
            config.stages[i].imitateRadius = Math.max(1.0, config.stages[i].imitateRadius);
        }
        return config;
    }

    private static ImitateConfig createDefault() {
        ImitateConfig config = new ImitateConfig();
        for (int i = 0; i < STAGES; i++) {
            int stage = i + 1;
            config.stages[i] = new StageConfig();
            config.stages[i].name = "阶段 " + stage;
            config.stages[i].durationSeconds = 0;
            config.stages[i].switchCooldownSeconds = 0;
            config.stages[i].soundWaveCooldownSeconds = 0;
            config.stages[i].soundWaveRadius = 10.0 + stage * 2.0;
            config.stages[i].soundWaveEffectDuration = 5 + stage;
            config.stages[i].silenceRadius = 10.0 + stage * 1.0;
            config.stages[i].silenceDuration = 10 + stage * 2;
            config.stages[i].silenceCooldown = 0;
            config.stages[i].imitateRadius = 5.0;
        }
        return config;
    }

    public void save() {
        LOGGER.info("保存配置到文件: {}", CONFIG_PATH.toFile().getAbsolutePath());
        try (Writer writer = new FileWriter(CONFIG_PATH.toFile())) {
            GSON.toJson(this, writer);
            LOGGER.info("配置已保存: 阶段1切换冷却={}, 阶段1声波冷却={}", 
                stages[0].switchCooldownSeconds, stages[0].soundWaveCooldownSeconds);
        } catch (IOException e) {
            LOGGER.error("保存配置文件失败: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static ImitateConfig fromJson(String json) {
        return normalize(GSON.fromJson(json, ImitateConfig.class));
    }

    public int getDuration(int stage) { return stages[stage - 1].durationSeconds; }
    public int getSwitchCooldown(int stage) { return stages[stage - 1].switchCooldownSeconds; }
    public int getSoundWaveCooldown(int stage) { return stages[stage - 1].soundWaveCooldownSeconds; }
    public double getSoundWaveRadius(int stage) { return stages[stage - 1].soundWaveRadius; }
    public int getSoundWaveEffectDuration(int stage) { return stages[stage - 1].soundWaveEffectDuration; }
    public double getSilenceRadius(int stage) { return stages[stage - 1].silenceRadius; }
    public int getSilenceDuration(int stage) { return stages[stage - 1].silenceDuration; }
    public int getSilenceCooldown(int stage) { return stages[stage - 1].silenceCooldown; }
    public double getImitateRadius(int stage) { return stages[stage - 1].imitateRadius; }

    public void setDuration(int stage, int val) { stages[stage - 1].durationSeconds = Math.max(0, val); save(); }
    public void setSwitchCooldown(int stage, int val) { stages[stage - 1].switchCooldownSeconds = Math.max(0, val); save(); }
    public void setSoundWaveCooldown(int stage, int val) { stages[stage - 1].soundWaveCooldownSeconds = Math.max(0, val); save(); }
    public void setSoundWaveRadius(int stage, double val) { stages[stage - 1].soundWaveRadius = Math.max(1.0, val); save(); }
    public void setSoundWaveEffectDuration(int stage, int val) { stages[stage - 1].soundWaveEffectDuration = Math.max(1, val); save(); }
    public void setSilenceRadius(int stage, double val) { stages[stage - 1].silenceRadius = Math.max(1.0, val); save(); }
    public void setSilenceDuration(int stage, int val) { stages[stage - 1].silenceDuration = Math.max(1, val); save(); }
    public void setSilenceCooldown(int stage, int val) { stages[stage - 1].silenceCooldown = Math.max(0, val); save(); }
    public void setImitateRadius(int stage, double val) { stages[stage - 1].imitateRadius = Math.max(1.0, val); save(); }

    public int getSoundWaveUnlockThreshold() { return soundWaveUnlockThreshold; }
    public int getSilenceUnlockThreshold() { return silenceUnlockThreshold; }
    public int getAreaSelectUnlockThreshold() { return areaSelectUnlockThreshold; }
    public void setSoundWaveUnlockThreshold(int val) { soundWaveUnlockThreshold = Math.max(0, Math.min(100, val)); save(); }
    public void setSilenceUnlockThreshold(int val) { silenceUnlockThreshold = Math.max(0, Math.min(100, val)); save(); }
    public void setAreaSelectUnlockThreshold(int val) { areaSelectUnlockThreshold = Math.max(0, Math.min(100, val)); save(); }
}