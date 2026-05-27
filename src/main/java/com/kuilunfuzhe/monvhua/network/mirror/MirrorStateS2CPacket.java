package com.kuilunfuzhe.monvhua.network.mirror;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public record MirrorStateS2CPacket(
	boolean slot1Active, double hsX1, double hsY1, double hsZ1,
	double mapX1, double mapY1, double mapZ1, double radius1,
	boolean slot2Active, double hsX2, double hsY2, double hsZ2,
	double mapX2, double mapY2, double mapZ2, double radius2,
	boolean viewportActive
) implements CustomPayload {
	public static final Id<MirrorStateS2CPacket> ID = new Id<>(Identifier.of("monvhua", "mirror_state"));
	public static final PacketCodec<PacketByteBuf, MirrorStateS2CPacket> CODEC = PacketCodec.of(
		(MirrorStateS2CPacket value, PacketByteBuf buf) -> {
			buf.writeBoolean(value.slot1Active);
			buf.writeDouble(value.hsX1); buf.writeDouble(value.hsY1); buf.writeDouble(value.hsZ1);
			buf.writeDouble(value.mapX1); buf.writeDouble(value.mapY1); buf.writeDouble(value.mapZ1);
			buf.writeDouble(value.radius1);
			buf.writeBoolean(value.slot2Active);
			buf.writeDouble(value.hsX2); buf.writeDouble(value.hsY2); buf.writeDouble(value.hsZ2);
			buf.writeDouble(value.mapX2); buf.writeDouble(value.mapY2); buf.writeDouble(value.mapZ2);
			buf.writeDouble(value.radius2);
			buf.writeBoolean(value.viewportActive);
		},
		(PacketByteBuf buf) -> {
			boolean s1a = buf.readBoolean();
			double hsx1 = buf.readDouble(), hsy1 = buf.readDouble(), hsz1 = buf.readDouble();
			double mpx1 = buf.readDouble(), mpy1 = buf.readDouble(), mpz1 = buf.readDouble();
			double r1 = buf.readDouble();
			boolean s2a = buf.readBoolean();
			double hsx2 = buf.readDouble(), hsy2 = buf.readDouble(), hsz2 = buf.readDouble();
			double mpx2 = buf.readDouble(), mpy2 = buf.readDouble(), mpz2 = buf.readDouble();
			double r2 = buf.readDouble();
			boolean va = buf.readBoolean();
			return new MirrorStateS2CPacket(
				s1a, hsx1, hsy1, hsz1, mpx1, mpy1, mpz1, r1,
				s2a, hsx2, hsy2, hsz2, mpx2, mpy2, mpz2, r2, va
			);
		}
	);

	public Vec3d getHsPos1() { return slot1Active ? new Vec3d(hsX1, hsY1, hsZ1) : null; }
	public Vec3d getMapPos1() { return slot1Active ? new Vec3d(mapX1, mapY1, mapZ1) : null; }
	public Vec3d getHsPos2() { return slot2Active ? new Vec3d(hsX2, hsY2, hsZ2) : null; }
	public Vec3d getMapPos2() { return slot2Active ? new Vec3d(mapX2, mapY2, mapZ2) : null; }

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}

	private static boolean registered = false;

	public static void register() {
		if (!registered) {
			PayloadTypeRegistry.playS2C().register(ID, CODEC);
			registered = true;
		}
	}
}
