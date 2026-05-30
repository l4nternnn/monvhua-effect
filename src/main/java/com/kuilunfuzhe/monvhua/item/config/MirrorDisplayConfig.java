package com.kuilunfuzhe.monvhua.item.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Path;

/**
 * 镜像显示配置文件
 * 控制镜中镜屏幕的显示尺寸和扇形渲染参数（角度、旋转），支持 JSON 持久化和参数钳位校验
 */
public class MirrorDisplayConfig {
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("mirror_display.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static MirrorDisplayConfig instance;

    // 显示尺寸（逻辑像素，即 FBO 分辨率，也是屏幕显示大小）
    public int displayWidth = 427;
    public int displayHeight = 451;

    // 扇形参数
    public float sectorAngle = 270f;      // 角度 0-360
    public float sectorRotation = 0f;     // 中轴指向（度，顺时针从正上方）

    public static MirrorDisplayConfig getInstance() {
        if (instance == null) instance = load();
        return instance;
    }

    public static void reload() {
        instance = load();
    }

    private static MirrorDisplayConfig load() {
        if (CONFIG_PATH.toFile().exists()) {
            try (Reader reader = new FileReader(CONFIG_PATH.toFile())) {
                MirrorDisplayConfig config = GSON.fromJson(reader, MirrorDisplayConfig.class);
                if (config == null) return createDefault();
                // 钳位参数防止异常
                config.displayWidth = Math.max(64, Math.min(1920, config.displayWidth));
                config.displayHeight = Math.max(64, Math.min(1080, config.displayHeight));
                config.sectorAngle = Math.max(0, Math.min(360, config.sectorAngle));
                return config;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return createDefault();
    }

    private static MirrorDisplayConfig createDefault() {
        return new MirrorDisplayConfig();
    }

    public void save() {
        try (Writer writer = new FileWriter(CONFIG_PATH.toFile())) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static MirrorDisplayConfig fromJson(String json) {
        return GSON.fromJson(json, MirrorDisplayConfig.class);
    }
}