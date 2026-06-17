package com.kuilunfuzhe.monvhua.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class BodyPoseDefaultsConfig {
    public static final String MODE_STATIC_PART = "static_part";
    public static final String MODE_SKELETAL = "skeletal";
    public static final String MODE_TRUE_SKELETAL = "true_skeletal";

    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("monvhua_body_pose_defaults.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static BodyPoseDefaultsConfig instance;

    private boolean defaultSlimModel = true;
    private String defaultPoseMode = MODE_TRUE_SKELETAL;

    private BodyPoseDefaultsConfig() {
    }

    public static synchronized BodyPoseDefaultsConfig getInstance() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    public static boolean getDefaultSlimModel() {
        return getInstance().defaultSlimModel;
    }

    public static String getDefaultPoseMode() {
        return getInstance().defaultPoseMode;
    }

    public static void setDefaultSlimModel(boolean slimModel) {
        BodyPoseDefaultsConfig config = getInstance();
        config.defaultSlimModel = slimModel;
        config.save();
    }

    public static void setDefaultPoseMode(String poseMode) {
        BodyPoseDefaultsConfig config = getInstance();
        config.defaultPoseMode = normalizePoseMode(poseMode);
        config.save();
    }

    public static void update(boolean slimModel, String poseMode) {
        BodyPoseDefaultsConfig config = getInstance();
        config.defaultSlimModel = slimModel;
        config.defaultPoseMode = normalizePoseMode(poseMode);
        config.save();
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

    private static BodyPoseDefaultsConfig load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
                BodyPoseDefaultsConfig config = GSON.fromJson(reader, BodyPoseDefaultsConfig.class);
                if (config != null) {
                    config.defaultPoseMode = normalizePoseMode(config.defaultPoseMode);
                    return config;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        BodyPoseDefaultsConfig config = new BodyPoseDefaultsConfig();
        config.save();
        return config;
    }

    public static String normalizePoseMode(String poseMode) {
        return switch (poseMode == null ? "" : poseMode) {
            case MODE_STATIC_PART -> MODE_STATIC_PART;
            case MODE_SKELETAL -> MODE_SKELETAL;
            case MODE_TRUE_SKELETAL -> MODE_TRUE_SKELETAL;
            default -> MODE_TRUE_SKELETAL;
        };
    }
}
