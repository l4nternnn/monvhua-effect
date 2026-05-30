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

public class MirrorDataStore {
	private static final Path DATA_PATH = FabricLoader.getInstance().getConfigDir().resolve("mirror_data.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Logger LOGGER = LoggerFactory.getLogger("mirror_data");

	/**
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

	public static class PlayerData {
		public SlotData[] slots = new SlotData[2];
	}

	private static final Map<UUID, PlayerData> cache = new HashMap<>();

	public static PlayerData getOrCreate(UUID uuid) {
		return cache.computeIfAbsent(uuid, k -> new PlayerData());
	}

	public static void setSlot(UUID uuid, int slot, Vec3d hsPos, Vec3d mapPos, double radius) {
		PlayerData data = getOrCreate(uuid);
		data.slots[slot - 1] = new SlotData(hsPos, mapPos, radius);
		save();
	}

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

	public static void removeSlot(UUID uuid, int slot) {
		PlayerData data = cache.get(uuid);
		if (data != null) {
			data.slots[slot - 1] = null;
			save();
		}
	}

	public static void clearPlayer(UUID uuid) {
		cache.remove(uuid);
		save();
	}

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
