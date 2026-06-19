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

public class PaintConfig {
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("paint.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static PaintConfig instance;

    public double brushConsumptionMultiplier = 1.0;
    public int bucketBrushLoads = 2;

    public static PaintConfig getInstance() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    public static void setInstance(PaintConfig config) {
        instance = sanitize(config);
        instance.save();
    }

    public static void syncInstance(PaintConfig config) {
        instance = sanitize(config);
    }

    private static PaintConfig load() {
        if (Files.isRegularFile(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
                return sanitize(GSON.fromJson(reader, PaintConfig.class));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        PaintConfig config = new PaintConfig();
        config.save();
        return config;
    }

    private static PaintConfig sanitize(PaintConfig config) {
        if (config == null) {
            config = new PaintConfig();
        }
        config.brushConsumptionMultiplier = Math.clamp(config.brushConsumptionMultiplier, 0.0, 100.0);
        config.bucketBrushLoads = Math.clamp(config.bucketBrushLoads, 0, 999);
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

    public static PaintConfig fromJson(String json) {
        return sanitize(GSON.fromJson(json, PaintConfig.class));
    }

    public double scaledConsumption(int changedPixels) {
        if (changedPixels <= 0 || brushConsumptionMultiplier <= 0.0) {
            return 0.0D;
        }
        return changedPixels * brushConsumptionMultiplier;
    }
}
