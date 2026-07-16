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
    public double spraySeconds = 1.0D;
    public double sprayRampUpSeconds = 0.15D;
    public double sprayDecaySeconds = 1.0D;
    public int particlesPerSecond = 28;
    public double bloodSpotFadeSeconds = 6.0D;
    public double bloodSpotButterflyChancePercent = 0.0D;
    public double bloodButterflyLifetimeSeconds = 8.0D;
    public double triggerIntervalSeconds = 0.0D;
    public String entitySelector = "";

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
                InjuredBleedingConfig config = sanitize(GSON.fromJson(reader, InjuredBleedingConfig.class));
                config.save();
                return config;
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
        if (config.spraySeconds <= 0.0D && config.sprayTicks > 0) {
            config.spraySeconds = config.sprayTicks / 20.0D;
        }
        if (config.sprayDecaySeconds <= 0.0D) {
            config.sprayDecaySeconds = config.spraySeconds;
        }
        config.spraySeconds = Math.clamp(config.spraySeconds, 0.05D, 20.0D);
        config.sprayRampUpSeconds = Math.clamp(config.sprayRampUpSeconds, 0.0D, 20.0D);
        config.sprayDecaySeconds = Math.clamp(config.sprayDecaySeconds, 0.05D, 20.0D);
        config.sprayTicks = Math.clamp((int) Math.round((config.sprayRampUpSeconds + config.sprayDecaySeconds) * 20.0D), 1, 800);
        config.particlesPerSecond = Math.clamp(config.particlesPerSecond, 1, 240);
        config.bloodSpotFadeSeconds = Math.clamp(config.bloodSpotFadeSeconds, 0.5D, 120.0D);
        config.bloodSpotButterflyChancePercent = Math.clamp(config.bloodSpotButterflyChancePercent, 0.0D, 100.0D);
        config.bloodButterflyLifetimeSeconds = Math.clamp(config.bloodButterflyLifetimeSeconds, 0.1D, 32.0D);
        config.triggerIntervalSeconds = Math.clamp(config.triggerIntervalSeconds, 0.0D, 3600.0D);
        config.entitySelector = normalizeEntitySelector(config.entitySelector);
        return config;
    }

    public static String normalizeEntitySelector(String selector) {
        if (selector == null) {
            return "";
        }
        String input = selector.trim();
        if (input.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder(input.length());
        boolean inQuote = false;
        char quote = 0;
        boolean escaped = false;
        boolean skipWhitespace = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (inQuote) {
                result.append(c);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == quote) {
                    inQuote = false;
                }
                continue;
            }
            if (c == '\'' || c == '"') {
                inQuote = true;
                quote = c;
                result.append(c);
                skipWhitespace = false;
                continue;
            }
            if (Character.isWhitespace(c)) {
                if (skipWhitespace || nextNonWhitespace(input, i + 1) == ']') {
                    continue;
                }
                result.append(c);
                continue;
            }
            if (c == '[' || c == ',' || c == '=') {
                removeTrailingWhitespace(result);
                result.append(c);
                skipWhitespace = true;
                continue;
            }
            result.append(c);
            skipWhitespace = false;
        }
        return result.toString();
    }

    private static void removeTrailingWhitespace(StringBuilder builder) {
        while (!builder.isEmpty() && Character.isWhitespace(builder.charAt(builder.length() - 1))) {
            builder.deleteCharAt(builder.length() - 1);
        }
    }

    private static char nextNonWhitespace(String text, int start) {
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isWhitespace(c)) {
                return c;
            }
        }
        return 0;
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
