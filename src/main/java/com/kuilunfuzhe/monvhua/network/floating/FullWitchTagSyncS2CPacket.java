package com.kuilunfuzhe.monvhua.network.floating;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record FullWitchTagSyncS2CPacket(boolean hasFullWitchTag, boolean hasFullWitchFlight, boolean hasFloatingTag) implements CustomPayload {
    public static final Id<FullWitchTagSyncS2CPacket> ID = new Id<>(Identifier.of("monvhua", "full_witch_tag_sync"));
    public static final PacketCodec<RegistryByteBuf, FullWitchTagSyncS2CPacket> CODEC = PacketCodec.of(
            FullWitchTagSyncS2CPacket::write,
            FullWitchTagSyncS2CPacket::new
    );

    private static boolean registered = false;

    private FullWitchTagSyncS2CPacket(RegistryByteBuf buf) {
        this(buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
    }

    private void write(RegistryByteBuf buf) {
        buf.writeBoolean(hasFullWitchTag);
        buf.writeBoolean(hasFullWitchFlight);
        buf.writeBoolean(hasFloatingTag);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public static void register() {
        if (!registered) {
            PayloadTypeRegistry.playS2C().register(ID, CODEC);
            registered = true;
        }
    }
}