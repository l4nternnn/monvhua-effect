package com.kuilunfuzhe.monvhua.item.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Path;

/**
 * 镜像配置文件
 * 管理镜中镜功能的阶段参数（观察时间、成功率、最大查看次数、触发半径、充能时间），支持 JSON 持久化读写
 */
public class MirrorConfig {
	/** 镜像的阶段总数 */
	private static final int STAGES = 7;
	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("mirror.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static MirrorConfig instance;

	/** 各阶段的配置数组（索引 0 对应阶段 1） */
	public StageConfig[] stages = new StageConfig[STAGES];

	/**
	 * 单阶段配置项
	 */
	public static class StageConfig {
		/** 观察时间（秒），玩家需要持续注视视口的最短时长 */
		public int watchTime = 3;
		/** 成功率（0.0-1.0），每次观察触发效果的概率 */
		public double successRate = 0.5;
		/** 最大成功查看次数，达到后该阶段不再触发 */
		public int viewCount = 1;
		/** 触发半径（格） */
		public double radius = 10.0;
		/** 充能时间（tick），默认 60 = 3 秒 */
		public int chargeTime = 60;
	}

	public MirrorConfig() {
		stages = new StageConfig[STAGES];
		for (int i = 0; i < STAGES; i++) {
			stages[i] = new StageConfig();
		}
	}

	/**
	 * @return 单例配置实例（首次调用时从文件加载或创建默认值）
	 */
	public static MirrorConfig getInstance() {
		if (instance == null) instance = load();
		return instance;
	}

	/**
	 * 替换当前配置实例并立即保存到文件
	 * @param config 新配置对象
	 */
	public static void setInstance(MirrorConfig config) {
		instance = config;
		instance.save();
	}

	/** 从 JSON 文件加载配置，文件不存在或格式异常时创建默认值 */
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

	/**
	 * 创建带默认值的配置（首次运行或配置文件损坏时使用）
	 * 默认值按阶段递增：观察时间递减 4→1，成功率递增 0.2→0.8，查看次数分三档，充能时间递减
	 */
	private static MirrorConfig createDefault() {
		MirrorConfig config = new MirrorConfig();
		for (int i = 0; i < STAGES; i++) {
			int stage = i + 1;
			config.stages[i] = new StageConfig();
			config.stages[i].watchTime = Math.max(1, 5 - stage); // 阶段越高观察时间越短
			config.stages[i].successRate = 0.1 + stage * 0.1; // 0.2 ~ 0.8 线性递增
			config.stages[i].viewCount = stage <= 2 ? 1 : (stage <= 4 ? 3 : 5); // 分三档
			config.stages[i].chargeTime = Math.max(20, 80 - (stage - 1) * 10); // 递减，最低 20 tick
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
	public int getChargeTime(int stage) { return stages[stage - 1].chargeTime; }

	public void setWatchTime(int stage, int val) { stages[stage - 1].watchTime = val; save(); }
	public void setSuccessRate(int stage, double val) { stages[stage - 1].successRate = val; save(); }
	public void setViewCount(int stage, int val) { stages[stage - 1].viewCount = val; save(); }
	public void setRadius(int stage, double val) { stages[stage - 1].radius = val; save(); }
	public void setChargeTime(int stage, int val) { stages[stage - 1].chargeTime = val; save(); }
}
