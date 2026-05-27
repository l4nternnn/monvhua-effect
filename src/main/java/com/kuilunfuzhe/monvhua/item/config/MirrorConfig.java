package com.kuilunfuzhe.monvhua.item.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Path;

public class MirrorConfig {
	private static final int STAGES = 7;
	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("mirror.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static MirrorConfig instance;

	public StageConfig[] stages = new StageConfig[STAGES];

	public static class StageConfig {
		public int watchTime = 3;        // seconds viewport must be active
		public double successRate = 0.5; // probability (0.0-1.0)
		public int viewCount = 1;        // max successful views
		public double radius = 10.0;     // 触发半径
	}

	public MirrorConfig() {
		stages = new StageConfig[STAGES];
		for (int i = 0; i < STAGES; i++) {
			stages[i] = new StageConfig();
		}
	}

	public static MirrorConfig getInstance() {
		if (instance == null) instance = load();
		return instance;
	}

	public static void setInstance(MirrorConfig config) {
		instance = config;
		instance.save();
	}

	private static MirrorConfig load() {
		if (CONFIG_PATH.toFile().exists()) {
			try (Reader reader = new FileReader(CONFIG_PATH.toFile())) {
				MirrorConfig config = GSON.fromJson(reader, MirrorConfig.class);
				if (config.stages == null || config.stages.length != STAGES) return createDefault();
				return config;
			} catch (IOException e) { e.printStackTrace(); }
		}
		return createDefault();
	}

	private static MirrorConfig createDefault() {
		MirrorConfig config = new MirrorConfig();
		for (int i = 0; i < STAGES; i++) {
			int stage = i + 1;
			config.stages[i] = new StageConfig();
			config.stages[i].watchTime = Math.max(1, 5 - stage);
			config.stages[i].successRate = 0.1 + stage * 0.1;
			config.stages[i].viewCount = stage <= 2 ? 1 : (stage <= 4 ? 3 : 5);
		}
		return config;
	}

	public void save() {
		try (Writer writer = new FileWriter(CONFIG_PATH.toFile())) {
			GSON.toJson(this, writer);
		} catch (IOException e) { e.printStackTrace(); }
	}

	public String toJson() {
		return GSON.toJson(this);
	}

	public static MirrorConfig fromJson(String json) {
		return GSON.fromJson(json, MirrorConfig.class);
	}

	public int getWatchTime(int stage) { return stages[stage - 1].watchTime; }
	public double getSuccessRate(int stage) { return stages[stage - 1].successRate; }
	public int getViewCount(int stage) { return stages[stage - 1].viewCount; }
	public double getRadius(int stage) { return stages[stage - 1].radius; }

	public void setWatchTime(int stage, int val) { stages[stage - 1].watchTime = val; save(); }
	public void setSuccessRate(int stage, double val) { stages[stage - 1].successRate = val; save(); }
	public void setViewCount(int stage, int val) { stages[stage - 1].viewCount = val; save(); }
	public void setRadius(int stage, double val) { stages[stage - 1].radius = val; save(); }
}
