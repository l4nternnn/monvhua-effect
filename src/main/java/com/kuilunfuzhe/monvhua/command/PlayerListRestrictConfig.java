package com.kuilunfuzhe.monvhua.command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Path;

/**
 * 玩家列表限制的全局配置。
 * 持久化到 config/monvhua_playerlist_restrict.json，
 * 服务器重启后状态不丢失。
 */
public final class PlayerListRestrictConfig {
    private static final Path CONFIG_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("monvhua_playerlist_restrict.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static PlayerListRestrictConfig instance;

    private boolean enabled = false;

    private PlayerListRestrictConfig() {
    }

    /**
     * 获取单例，首次调用时从磁盘加载。
     */
    public static PlayerListRestrictConfig getInstance() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置启用状态并立即持久化到磁盘。
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        save();
    }

    private static PlayerListRestrictConfig load() {
        if (CONFIG_PATH.toFile().exists()) {
            try (Reader reader = new FileReader(CONFIG_PATH.toFile())) {
                PlayerListRestrictConfig config = GSON.fromJson(reader, PlayerListRestrictConfig.class);
                if (config != null) {
                    return config;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        PlayerListRestrictConfig config = new PlayerListRestrictConfig();
        config.save();
        return config;
    }

    private void save() {
        try (Writer writer = new FileWriter(CONFIG_PATH.toFile())) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
