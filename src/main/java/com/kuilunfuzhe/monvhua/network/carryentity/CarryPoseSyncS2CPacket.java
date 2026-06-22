package com.kuilunfuzhe.monvhua.network.carryentity;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record CarryPoseSyncS2CPacket(int entityId, int pose, int partnerId) implements CustomPayload {
	public static final int POSE_NONE = 0;
	public static final int POSE_CARRIER = 1;
	public static final int POSE_CARRIED = 2;
	public static final int POSE_BUCKET_CARRIER = 3;
	public static final int POSE_CARRIED_DRAG = 4;

	public static final CustomPayload.Id<CarryPoseSyncS2CPacket> ID =
		new CustomPayload.Id<>(Identifier.of("monvhua", "carry_pose_sync"));

	public static final PacketCodec<RegistryByteBuf, CarryPoseSyncS2CPacket> CODEC =
		PacketCodec.tuple(
			PacketCodecs.INTEGER, CarryPoseSyncS2CPacket::entityId,
			PacketCodecs.INTEGER, CarryPoseSyncS2CPacket::pose,
			PacketCodecs.INTEGER, CarryPoseSyncS2CPacket::partnerId,
			CarryPoseSyncS2CPacket::new
		);

	private static boolean registered = false;

	public static void register() {
		if (!registered) {
			PayloadTypeRegistry.playS2C().register(ID, CODEC);
			registered = true;
		}
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
