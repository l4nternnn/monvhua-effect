package com.kuilunfuzhe.monvhua.features.carryentity;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.kuilunfuzhe.monvhua.network.carryentity.CarryTransformPackets;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class CarryTransformConfig {
	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("monvhua_carry_transform.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static CarryTransformConfig instance;

	public TransformSet princess = new TransformSet();
	public TransformSet drag = new TransformSet();
	private PoseTransform pose;
	private ViewTransform view;

	private CarryTransformConfig() {
	}

	public static synchronized CarryTransformConfig getInstance() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	public static synchronized CarryTransformConfig createDefault() {
		return new CarryTransformConfig();
	}

	public static synchronized CarryTransformConfig fromJson(String json) {
		try {
			return sanitize(GSON.fromJson(json, CarryTransformConfig.class));
		} catch (Exception ignored) {
			return createDefault();
		}
	}

	public TransformSet transformFor(int poseMode) {
		return poseMode == CarryTransformPackets.POSE_DRAG ? drag : princess;
	}

	public static synchronized CarryTransformConfig setPoseTransform(int poseMode, float x, float y, float z, float pitch, float yaw, float roll) {
		CarryTransformConfig config = getInstance();
		TransformSet transform = config.transformFor(poseMode);
		transform.pose.offsetX = finite(x);
		transform.pose.offsetY = finite(y);
		transform.pose.offsetZ = finite(z);
		transform.pose.pitchDegrees = finite(pitch);
		transform.pose.yawDegrees = finite(yaw);
		transform.pose.rollDegrees = finite(roll);
		config.save();
		return config;
	}

	public static synchronized CarryTransformConfig addPoseTransform(int poseMode, float x, float y, float z, float pitch, float yaw, float roll) {
		TransformSet transform = getInstance().transformFor(poseMode);
		return setPoseTransform(
				poseMode,
				transform.pose.offsetX + x,
				transform.pose.offsetY + y,
				transform.pose.offsetZ + z,
				transform.pose.pitchDegrees + pitch,
				transform.pose.yawDegrees + yaw,
				transform.pose.rollDegrees + roll
		);
	}

	public static synchronized CarryTransformConfig resetPoseTransform(int poseMode) {
		return setPoseTransform(poseMode, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
	}

	public static synchronized CarryTransformConfig setViewTransform(int poseMode, float x, float y, float z, float pitch, float yaw, float roll) {
		CarryTransformConfig config = getInstance();
		TransformSet transform = config.transformFor(poseMode);
		transform.view.enabled = true;
		transform.view.offsetX = finite(x);
		transform.view.offsetY = finite(y);
		transform.view.offsetZ = finite(z);
		transform.view.pitchDegrees = finite(pitch);
		transform.view.yawDegrees = finite(yaw);
		transform.view.rollDegrees = finite(roll);
		config.save();
		return config;
	}

	public static synchronized CarryTransformConfig addViewTransform(int poseMode, float x, float y, float z, float pitch, float yaw, float roll) {
		TransformSet transform = getInstance().transformFor(poseMode);
		return setViewTransform(
				poseMode,
				transform.view.offsetX + x,
				transform.view.offsetY + y,
				transform.view.offsetZ + z,
				transform.view.pitchDegrees + pitch,
				transform.view.yawDegrees + yaw,
				transform.view.rollDegrees + roll
		);
	}

	public static synchronized CarryTransformConfig resetViewTransform(int poseMode) {
		CarryTransformConfig config = getInstance();
		ViewTransform view = config.transformFor(poseMode).view;
		view.enabled = false;
		view.offsetX = 0.0F;
		view.offsetY = 0.0F;
		view.offsetZ = 0.0F;
		view.pitchDegrees = 0.0F;
		view.yawDegrees = 0.0F;
		view.rollDegrees = 0.0F;
		config.save();
		return config;
	}

	public static synchronized CarryTransformConfig applyRequest(CarryTransformPackets.UpdateC2S packet) {
		int poseMode = sanitizePoseMode(packet.poseMode());
		if (packet.target() == CarryTransformPackets.TARGET_POSE) {
			return switch (packet.action()) {
				case CarryTransformPackets.ACTION_ADD -> addPoseTransform(poseMode, packet.x(), packet.y(), packet.z(), packet.pitch(), packet.yaw(), packet.roll());
				case CarryTransformPackets.ACTION_RESET -> resetPoseTransform(poseMode);
				default -> setPoseTransform(poseMode, packet.x(), packet.y(), packet.z(), packet.pitch(), packet.yaw(), packet.roll());
			};
		}
		if (packet.target() == CarryTransformPackets.TARGET_VIEW) {
			return switch (packet.action()) {
				case CarryTransformPackets.ACTION_ADD -> addViewTransform(poseMode, packet.x(), packet.y(), packet.z(), packet.pitch(), packet.yaw(), packet.roll());
				case CarryTransformPackets.ACTION_RESET -> resetViewTransform(poseMode);
				default -> setViewTransform(poseMode, packet.x(), packet.y(), packet.z(), packet.pitch(), packet.yaw(), packet.roll());
			};
		}
		return getInstance();
	}

	public static void syncTo(ServerPlayerEntity player) {
		if (player == null) {
			return;
		}
		ServerPlayNetworking.send(player, new CarryTransformPackets.ConfigS2C(getInstance().toJson()));
	}

	public static void syncToAll(MinecraftServer server) {
		if (server == null) {
			return;
		}
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			syncTo(player);
		}
	}

	public String toJson() {
		return GSON.toJson(this);
	}

	public String formatPose(int poseMode) {
		PoseTransform pose = transformFor(poseMode).pose;
		return formatSix(pose.offsetX, pose.offsetY, pose.offsetZ, pose.pitchDegrees, pose.yawDegrees, pose.rollDegrees);
	}

	public String formatView(int poseMode) {
		ViewTransform view = transformFor(poseMode).view;
		return "enabled=" + view.enabled + " " + formatSix(view.offsetX, view.offsetY, view.offsetZ, view.pitchDegrees, view.yawDegrees, view.rollDegrees);
	}

	public static int sanitizePoseMode(int poseMode) {
		return poseMode == CarryTransformPackets.POSE_DRAG ? CarryTransformPackets.POSE_DRAG : CarryTransformPackets.POSE_PRINCESS;
	}

	public static String poseModeName(int poseMode) {
		return sanitizePoseMode(poseMode) == CarryTransformPackets.POSE_DRAG ? "drag" : "princess";
	}

	private void save() {
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static CarryTransformConfig load() {
		if (Files.isRegularFile(CONFIG_PATH)) {
			try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
				CarryTransformConfig config = sanitize(GSON.fromJson(reader, CarryTransformConfig.class));
				config.save();
				return config;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		CarryTransformConfig config = createDefault();
		config.save();
		return config;
	}

	private static CarryTransformConfig sanitize(CarryTransformConfig config) {
		if (config == null) {
			return createDefault();
		}
		if (config.princess == null) {
			config.princess = new TransformSet();
		}
		if (config.drag == null) {
			config.drag = new TransformSet();
		}
		if (config.pose != null) {
			config.princess.pose = config.pose;
			config.pose = null;
		}
		if (config.view != null) {
			config.princess.view = config.view;
			config.view = null;
		}
		config.princess.sanitize();
		config.drag.sanitize();
		return config;
	}

	private static float finite(float value) {
		return Float.isFinite(value) ? value : 0.0F;
	}

	private static String formatSix(float x, float y, float z, float pitch, float yaw, float roll) {
		return String.format(Locale.ROOT, "offset=(%.4f, %.4f, %.4f), rotation=(pitch=%.4f, yaw=%.4f, roll=%.4f)", x, y, z, pitch, yaw, roll);
	}

	public static final class TransformSet {
		public PoseTransform pose = new PoseTransform();
		public ViewTransform view = new ViewTransform();

		private void sanitize() {
			if (pose == null) {
				pose = new PoseTransform();
			}
			if (view == null) {
				view = new ViewTransform();
			}
			pose.sanitize();
			view.sanitize();
		}
	}

	public static final class PoseTransform {
		public float offsetX = 0.0F;
		public float offsetY = 0.0F;
		public float offsetZ = 0.0F;
		public float pitchDegrees = 0.0F;
		public float yawDegrees = 0.0F;
		public float rollDegrees = 0.0F;

		private void sanitize() {
			offsetX = finite(offsetX);
			offsetY = finite(offsetY);
			offsetZ = finite(offsetZ);
			pitchDegrees = finite(pitchDegrees);
			yawDegrees = finite(yawDegrees);
			rollDegrees = finite(rollDegrees);
		}
	}

	public static final class ViewTransform {
		public boolean enabled = false;
		public float offsetX = 0.0F;
		public float offsetY = 0.0F;
		public float offsetZ = 0.0F;
		public float pitchDegrees = 0.0F;
		public float yawDegrees = 0.0F;
		public float rollDegrees = 0.0F;

		private void sanitize() {
			offsetX = finite(offsetX);
			offsetY = finite(offsetY);
			offsetZ = finite(offsetZ);
			pitchDegrees = finite(pitchDegrees);
			yawDegrees = finite(yawDegrees);
			rollDegrees = finite(rollDegrees);
		}
	}
}
