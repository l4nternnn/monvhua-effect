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
    private static GravityConfig instance;

    public int forceDurationSeconds = 10;

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

    public static GravityConfig fromJson(String json) {
        return sanitize(GSON.fromJson(json, GravityConfig.class));
    }

    public int getForceDurationTicks() {
        return forceDurationSeconds * 20;
    }
}
