package com.kuilunfuzhe.monvhua.network.hold_hands;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record HoldHandsSyncS2CPacket(int entityId, boolean active, int handSide, int partnerId,
                                     float defaultDistance, float holdBodyYaw,
                                     float sharedHandX, float sharedHandY, float sharedHandZ) implements CustomPayload {
    public static final int HAND_LEFT = 0;
    public static final int HAND_RIGHT = 1;
    public static final int NO_PARTNER = -1;

    public static final CustomPayload.Id<HoldHandsSyncS2CPacket> ID =
            new CustomPayload.Id<>(Identifier.of("monvhua", "hold_hands_sync"));

    public static final PacketCodec<RegistryByteBuf, HoldHandsSyncS2CPacket> CODEC =
            PacketCodec.of(HoldHandsSyncS2CPacket::write, HoldHandsSyncS2CPacket::new);

    private static boolean registered;

    private HoldHandsSyncS2CPacket(RegistryByteBuf buf) {
        this(buf.readInt(), buf.readBoolean(), buf.readInt(), buf.readInt(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    private void write(RegistryByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeBoolean(active);
        buf.writeInt(handSide);
        buf.writeInt(partnerId);
        buf.writeFloat(defaultDistance);
        buf.writeFloat(holdBodyYaw);
        buf.writeFloat(sharedHandX);
        buf.writeFloat(sharedHandY);
        buf.writeFloat(sharedHandZ);
    }

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
