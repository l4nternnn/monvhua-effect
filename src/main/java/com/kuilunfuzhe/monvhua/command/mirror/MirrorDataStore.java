package com.kuilunfuzhe.monvhua.command.mirror;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 镜面数据的 JSON 持久化存储，使用 GSON 读写 {@code config/mirror_data.json}。
 * 内存缓存为 {@code Map<UUID, PlayerData>}，写操作后立即调用 save() 持久化。
 * load() 和 save() 均为 synchronized，保证线程安全。
 */
public class MirrorDataStore {
	/** 配置文件路径：{@code config/mirror_data.json} */
	private static final Path DATA_PATH = FabricLoader.getInstance().getConfigDir().resolve("mirror_data.json");
	/** 格式化输出的 GSON 实例 */
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Logger LOGGER = LoggerFactory.getLogger("mirror_data");

	/**
	 * 单个镜面槽位数据。
	 * 使用普通类 + raw double 字段代替 Java record / Vec3d，
	 * 避免 GSON 反序列化 record 或 final 字段时失败。
	 */
	public static class SlotData {
		public double hsX, hsY, hsZ;
		public double mapX, mapY, mapZ;
		public double radius;

		public SlotData() {}

		public SlotData(Vec3d hsPos, Vec3d mapPos, double radius) {
			this.hsX = hsPos.x; this.hsY = hsPos.y; this.hsZ = hsPos.z;
			this.mapX = mapPos.x; this.mapY = mapPos.y; this.mapZ = mapPos.z;
			this.radius = radius;
		}

		public Vec3d hsPos() { return new Vec3d(hsX, hsY, hsZ); }
		public Vec3d mapPos() { return new Vec3d(mapX, mapY, mapZ); }
		public double radius() { return radius; }
	}

	/** 玩家镜面数据，最多存储 2 个槽位 */
	public static class PlayerData {
		/** 镜面槽位数组，索引 0/1 分别对应镜面 1/2 */
		public SlotData[] slots = new SlotData[2];
	}

	/** 内存缓存：玩家 UUID → 镜面数据 */
	private static final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();

	/**
	 * 获取玩家数据，不存在则创建空数据。
	 * @return 玩家数据（不会返回 null）
	 */
	public static PlayerData getOrCreate(UUID uuid) {
		return cache.computeIfAbsent(uuid, k -> new PlayerData());
	}

	/**
	 * 设置指定槽位的完整镜面数据（发生点 + 映射点 + 半径），设置后立即持久化。
	 * @param slot 槽位编号（1 或 2）
	 */
	public static void setSlot(UUID uuid, int slot, Vec3d hsPos, Vec3d mapPos, double radius) {
		PlayerData data = getOrCreate(uuid);
		data.slots[slot - 1] = new SlotData(hsPos, mapPos, radius);
		save();
	}

	/**
	 * 设置指定槽位的发生点坐标，保留已有的 mapPos 和 radius（不存在时使用默认值）。
	 */
	public static void setHsPos(UUID uuid, int slot, Vec3d hsPos) {
		PlayerData data = getOrCreate(uuid);
		SlotData existing = data.slots[slot - 1];
		if (existing != null) {
			data.slots[slot - 1] = new SlotData(hsPos, existing.mapPos(), existing.radius);
		} else {
			data.slots[slot - 1] = new SlotData(hsPos, Vec3d.ZERO, 10.0);
		}
		save();
	}

	/**
	 * 设置指定槽位的映射点坐标，保留已有的 hsPos 和 radius（不存在时使用默认值）。
	 */
	public static void setMapPos(UUID uuid, int slot, Vec3d mapPos) {
		PlayerData data = getOrCreate(uuid);
		SlotData existing = data.slots[slot - 1];
		if (existing != null) {
			data.slots[slot - 1] = new SlotData(existing.hsPos(), mapPos, existing.radius);
		} else {
			data.slots[slot - 1] = new SlotData(Vec3d.ZERO, mapPos, 10.0);
		}
		save();
	}

	/**
	 * 设置指定槽位的触发半径，保留已有的 hsPos 和 mapPos（不存在时使用默认值）。
	 */
	public static void setRadius(UUID uuid, int slot, double radius) {
		PlayerData data = getOrCreate(uuid);
		SlotData existing = data.slots[slot - 1];
		if (existing != null) {
			data.slots[slot - 1] = new SlotData(existing.hsPos(), existing.mapPos(), radius);
		} else {
			data.slots[slot - 1] = new SlotData(Vec3d.ZERO, Vec3d.ZERO, radius);
		}
		save();
	}

	/**
	 * 移除指定槽位的镜面数据（置为 null），设置后立即持久化。
	 */
	public static void removeSlot(UUID uuid, int slot) {
		PlayerData data = cache.get(uuid);
		if (data != null) {
			data.slots[slot - 1] = null;
			save();
		}
	}

	/**
	 * 清除指定玩家的所有镜面数据（从缓存和持久化文件中移除）。
	 */
	public static void clearPlayer(UUID uuid) {
		cache.remove(uuid);
		save();
	}

	/**
	 * 从磁盘加载镜面数据到内存缓存（线程安全）。
	 * 如果文件不存在则跳过，加载失败则记录错误日志。
	 */
	public static synchronized void load() {
		File file = DATA_PATH.toFile();
		if (!file.exists()) {
			LOGGER.info("Mirror data file not found at {}", DATA_PATH);
			return;
		}
		try (Reader reader = new FileReader(file)) {
			Type type = new TypeToken<Map<UUID, PlayerData>>() {}.getType();
			Map<UUID, PlayerData> loaded = GSON.fromJson(reader, type);
			if (loaded != null) {
				cache.clear();
				cache.putAll(loaded);
				LOGGER.info("Loaded {} player mirror data entries", loaded.size());
			}
		} catch (IOException e) {
			LOGGER.error("Failed to load mirror data", e);
		}
	}

	/**
	 * 将内存缓存中的镜面数据保存到磁盘（线程安全）。
	 * 如果父目录不存在则自动创建，写入失败则记录错误日志。
	 */
	public static synchronized void save() {
		try {
			File file = DATA_PATH.toFile();
			File parent = file.getParentFile();
			if (parent != null && !parent.exists()) parent.mkdirs();
			try (Writer writer = new FileWriter(file)) {
				GSON.toJson(cache, writer);
			}
			LOGGER.info("Saved {} player mirror data entries", cache.size());
		} catch (IOException e) {
			LOGGER.error("Failed to save mirror data", e);
		}
	}
}
