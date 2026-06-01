package com.kuilunfuzhe.monvhua.network.bodypose;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record PlacePosedBodyC2SPacket(String skinName, boolean slimModel, float[] poseValues, boolean playerSkin, String playerName,
									  float offsetX, float offsetY, float offsetZ,
									  float rotationPitch, float rotationYaw, float rotationRoll) implements CustomPayload {
	public static final int POSE_VALUE_COUNT = 18;
	public static final Id<PlacePosedBodyC2SPacket> ID = new Id<>(Identifier.of("monvhua", "place_posed_body"));
	public static final PacketCodec<RegistryByteBuf, PlacePosedBodyC2SPacket> CODEC = PacketCodec.of(PlacePosedBodyC2SPacket::write, PlacePosedBodyC2SPacket::new);

	private static boolean registered = false;

	public PlacePosedBodyC2SPacket(String skinName, boolean slimModel, float[] poseValues) {
		this(skinName, slimModel, poseValues, false, "");
	}

	public PlacePosedBodyC2SPacket(String skinName, boolean slimModel, float[] poseValues, boolean playerSkin, String playerName) {
		this(skinName, slimModel, poseValues, playerSkin, playerName, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
	}

	public PlacePosedBodyC2SPacket {
		if (poseValues.length != POSE_VALUE_COUNT) {
			throw new IllegalArgumentException("Expected " + POSE_VALUE_COUNT + " pose values, got " + poseValues.length);
		}
		poseValues = poseValues.clone();
		playerName = playerName == null ? "" : playerName;
	}

	private PlacePosedBodyC2SPacket(RegistryByteBuf buf) {
		this(buf.readString(), buf.readBoolean(), readPoseValues(buf), buf.readBoolean(), buf.readString(),
				buf.readFloat(), buf.readFloat(), buf.readFloat(),
				buf.readFloat(), buf.readFloat(), buf.readFloat());
	}

	private void write(RegistryByteBuf buf) {
		buf.writeString(this.skinName);
		buf.writeBoolean(this.slimModel);
		for (float value : this.poseValues) {
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
	}

	private static float[] readPoseValues(RegistryByteBuf buf) {
		float[] values = new float[POSE_VALUE_COUNT];
		for (int i = 0; i < values.length; i++) {
			values[i] = buf.readFloat();
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
