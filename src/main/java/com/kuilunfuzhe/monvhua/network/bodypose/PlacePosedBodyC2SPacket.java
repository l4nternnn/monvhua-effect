package com.kuilunfuzhe.monvhua.network.bodypose;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record PlacePosedBodyC2SPacket(String skinName, boolean slimModel, float[] poseValues, float[] bendValues, boolean playerSkin, String playerName,
									  float offsetX, float offsetY, float offsetZ,
									  float rotationPitch, float rotationYaw, float rotationRoll,
									  float modelScale,
									  boolean fixedBase, double baseX, double baseY, double baseZ) implements CustomPayload {
	public static final int PART_COUNT = 6;
	public static final int POSE_VALUE_STRIDE = 4;
	public static final int ROTATION_VALUE_COUNT = PART_COUNT * 3;
	public static final int POSE_VALUE_COUNT = PART_COUNT * POSE_VALUE_STRIDE;
	public static final int BEND_VALUE_STRIDE = 3;
	public static final int BEND_VALUE_COUNT = PART_COUNT * BEND_VALUE_STRIDE;
	public static final Id<PlacePosedBodyC2SPacket> ID = new Id<>(Identifier.of("monvhua", "place_posed_body"));
	public static final PacketCodec<RegistryByteBuf, PlacePosedBodyC2SPacket> CODEC = PacketCodec.of(PlacePosedBodyC2SPacket::write, PlacePosedBodyC2SPacket::new);

	private static boolean registered = false;

	public PlacePosedBodyC2SPacket(String skinName, boolean slimModel, float[] poseValues) {
		this(skinName, slimModel, poseValues, null, false, "", 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F);
	}

	public PlacePosedBodyC2SPacket(String skinName, boolean slimModel, float[] poseValues, boolean playerSkin, String playerName) {
		this(skinName, slimModel, poseValues, null, playerSkin, playerName, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F);
	}

	public PlacePosedBodyC2SPacket(String skinName, boolean slimModel, float[] poseValues, boolean playerSkin, String playerName,
			float offsetX, float offsetY, float offsetZ, float rotationPitch, float rotationYaw, float rotationRoll) {
		this(skinName, slimModel, poseValues, null, playerSkin, playerName,
				offsetX, offsetY, offsetZ, rotationPitch, rotationYaw, rotationRoll, 1.0F);
	}

	public PlacePosedBodyC2SPacket(String skinName, boolean slimModel, float[] poseValues, boolean playerSkin, String playerName,
			float offsetX, float offsetY, float offsetZ, float rotationPitch, float rotationYaw, float rotationRoll, float modelScale) {
		this(skinName, slimModel, poseValues, null, playerSkin, playerName,
				offsetX, offsetY, offsetZ, rotationPitch, rotationYaw, rotationRoll, modelScale);
	}

	public PlacePosedBodyC2SPacket(String skinName, boolean slimModel, float[] poseValues, float[] bendValues, boolean playerSkin, String playerName,
			float offsetX, float offsetY, float offsetZ, float rotationPitch, float rotationYaw, float rotationRoll, float modelScale) {
		this(skinName, slimModel, poseValues, bendValues, playerSkin, playerName,
				offsetX, offsetY, offsetZ, rotationPitch, rotationYaw, rotationRoll, modelScale, false, 0.0D, 0.0D, 0.0D);
	}

	public PlacePosedBodyC2SPacket(String skinName, boolean slimModel, float[] poseValues, float[] bendValues, boolean playerSkin, String playerName,
			float offsetX, float offsetY, float offsetZ, float rotationPitch, float rotationYaw, float rotationRoll, float modelScale,
			double baseX, double baseY, double baseZ) {
		this(skinName, slimModel, poseValues, bendValues, playerSkin, playerName,
				offsetX, offsetY, offsetZ, rotationPitch, rotationYaw, rotationRoll, modelScale, true, baseX, baseY, baseZ);
	}

	public PlacePosedBodyC2SPacket(String skinName, boolean slimModel, float[] poseValues, boolean playerSkin, String playerName,
			float offsetX, float offsetY, float offsetZ, float rotationPitch, float rotationYaw, float rotationRoll, float modelScale,
			boolean fixedBase, double baseX, double baseY, double baseZ) {
		this(skinName, slimModel, poseValues, null, playerSkin, playerName,
				offsetX, offsetY, offsetZ, rotationPitch, rotationYaw, rotationRoll, modelScale, fixedBase, baseX, baseY, baseZ);
	}

	public PlacePosedBodyC2SPacket {
		poseValues = normalizePoseValues(poseValues);
		bendValues = normalizeBendValues(bendValues);
		playerName = playerName == null ? "" : playerName;
		modelScale = modelScale <= 0.0F ? 1.0F : modelScale;
	}

	private PlacePosedBodyC2SPacket(RegistryByteBuf buf) {
		this(buf.readString(), buf.readBoolean(), readPoseValues(buf), readBendValues(buf), buf.readBoolean(), buf.readString(),
				buf.readFloat(), buf.readFloat(), buf.readFloat(),
				buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
				buf.readBoolean(), buf.readDouble(), buf.readDouble(), buf.readDouble());
	}

	private void write(RegistryByteBuf buf) {
		buf.writeString(this.skinName);
		buf.writeBoolean(this.slimModel);
		for (float value : this.poseValues) {
			buf.writeFloat(value);
		}
		for (float value : this.bendValues) {
			buf.writeFloat(value);
		}
		buf.writeBoolean(this.playerSkin);
		buf.writeString(this.playerName);
		buf.writeFloat(this.offsetX);
		buf.writeFloat(this.offsetY);
		buf.writeFloat(this.offsetZ);
		buf.writeFloat(this.rotationPitch);
		buf.writeFloat(this.rotationYaw);
		buf.writeFloat(this.rotationRoll);
		buf.writeFloat(this.modelScale);
		buf.writeBoolean(this.fixedBase);
		buf.writeDouble(this.baseX);
		buf.writeDouble(this.baseY);
		buf.writeDouble(this.baseZ);
	}

	private static float[] readPoseValues(RegistryByteBuf buf) {
		float[] values = new float[POSE_VALUE_COUNT];
		for (int i = 0; i < values.length; i++) {
			values[i] = buf.readFloat();
		}
		return values;
	}

	private static float[] readBendValues(RegistryByteBuf buf) {
		float[] values = new float[BEND_VALUE_COUNT];
		for (int i = 0; i < values.length; i++) {
			values[i] = buf.readFloat();
		}
		return values;
	}

	private static float[] normalizePoseValues(float[] values) {
		if (values == null) {
			return createDefaultPoseValues();
		}
		if (values.length == POSE_VALUE_COUNT) {
			return values.clone();
		}
		if (values.length == ROTATION_VALUE_COUNT) {
			float[] normalized = createDefaultPoseValues();
			for (int part = 0; part < PART_COUNT; part++) {
				normalized[part * POSE_VALUE_STRIDE] = values[part * 3];
				normalized[part * POSE_VALUE_STRIDE + 1] = values[part * 3 + 1];
				normalized[part * POSE_VALUE_STRIDE + 2] = values[part * 3 + 2];
			}
			return normalized;
		}
		throw new IllegalArgumentException("Expected " + ROTATION_VALUE_COUNT + " or " + POSE_VALUE_COUNT
				+ " pose values, got " + values.length);
	}

	private static float[] normalizeBendValues(float[] values) {
		if (values == null) {
			return new float[BEND_VALUE_COUNT];
		}
		if (values.length == BEND_VALUE_COUNT) {
			return values.clone();
		}
		throw new IllegalArgumentException("Expected " + BEND_VALUE_COUNT + " bend values, got " + values.length);
	}

	private static float[] createDefaultPoseValues() {
		float[] values = new float[POSE_VALUE_COUNT];
		for (int part = 0; part < PART_COUNT; part++) {
			values[part * POSE_VALUE_STRIDE + 3] = 1.0F;
		}
		return values;
	}

	public static void register() {
		if (!registered) {
			PayloadTypeRegistry.playC2S().register(ID, CODEC);
			registered = true;
		}
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
