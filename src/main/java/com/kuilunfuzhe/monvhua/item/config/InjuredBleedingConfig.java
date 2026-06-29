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

public class InjuredBleedingConfig {
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("injured_bleeding.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static InjuredBleedingConfig instance;

    public int sprayTicks = 20;

    public static InjuredBleedingConfig getInstance() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    public static void setInstance(InjuredBleedingConfig config) {
        instance = sanitize(config);
        instance.save();
    }

    public static void syncInstance(InjuredBleedingConfig config) {
        instance = sanitize(config);
    }

    private static InjuredBleedingConfig load() {
        if (Files.isRegularFile(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
                return sanitize(GSON.fromJson(reader, InjuredBleedingConfig.class));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        InjuredBleedingConfig config = new InjuredBleedingConfig();
        config.save();
        return config;
    }

    private static InjuredBleedingConfig sanitize(InjuredBleedingConfig config) {
        if (config == null) {
            config = new InjuredBleedingConfig();
        }
        config.sprayTicks = Math.clamp(config.sprayTicks, 1, 200);
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

    public static InjuredBleedingConfig fromJson(String json) {
        return sanitize(GSON.fromJson(json, InjuredBleedingConfig.class));
    }
}
