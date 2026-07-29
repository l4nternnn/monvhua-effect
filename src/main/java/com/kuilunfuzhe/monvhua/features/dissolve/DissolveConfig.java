package com.kuilunfuzhe.monvhua.features.dissolve;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DissolveConfig {
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("dissolve.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static DissolveConfig instance;

    public double dissolveSpeed = 1.0D;
    public int pixelParticleLifetimeTicks = DissolveProfile.DEFAULT.pixelParticleLifetimeTicks();

    public static DissolveConfig getInstance() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    public static void setInstance(DissolveConfig config) {
        instance = sanitize(config);
        instance.save();
    }

    public static void syncInstance(DissolveConfig config) {
        instance = sanitize(config);
    }

    public static DissolveConfig fromValues(double dissolveSpeed, int pixelParticleLifetimeTicks) {
        DissolveConfig config = new DissolveConfig();
        config.dissolveSpeed = dissolveSpeed;
        config.pixelParticleLifetimeTicks = pixelParticleLifetimeTicks;
        return sanitize(config);
    }

    public DissolveProfile toProfile() {
        return toProfileWithDurationTicks(durationTicks());
    }

    public DissolveProfile toProfileWithDurationTicks(int durationTicks) {
        return DissolveProfile.DEFAULT
                .withDurationTicks(durationTicks)
                .withPixelParticleLifetimeTicks(pixelParticleLifetimeTicks);
    }

    public int durationTicks() {
        return Math.clamp((int) Math.round(DissolveProfile.DEFAULT.durationTicks() / dissolveSpeed), 1, 20 * 60);
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

    public static DissolveConfig fromJson(String json) {
        return sanitize(GSON.fromJson(json, DissolveConfig.class));
    }

    private static DissolveConfig load() {
        if (Files.isRegularFile(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
                DissolveConfig config = sanitize(GSON.fromJson(reader, DissolveConfig.class));
                config.save();
                return config;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        DissolveConfig config = new DissolveConfig();
        config.save();
        return config;
    }

    private static DissolveConfig sanitize(DissolveConfig config) {
        if (config == null) {
            config = new DissolveConfig();
        }
        if (!Double.isFinite(config.dissolveSpeed)) {
            config.dissolveSpeed = 1.0D;
        }
        config.dissolveSpeed = Math.clamp(config.dissolveSpeed, 0.05D, 20.0D);
        config.pixelParticleLifetimeTicks = Math.clamp(config.pixelParticleLifetimeTicks, 1, 20 * 10);
        return config;
    }
}
