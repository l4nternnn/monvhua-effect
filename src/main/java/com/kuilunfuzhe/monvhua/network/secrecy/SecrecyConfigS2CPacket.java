package com.kuilunfuzhe.monvhua.network.secrecy;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SecrecyConfigS2CPacket(String json) implements CustomPayload {
    public static final Id<SecrecyConfigS2CPacket> ID = new Id<>(Identifier.of("monvhua", "secrecy_config_sync"));
    public static final PacketCodec<RegistryByteBuf, SecrecyConfigS2CPacket> CODEC = PacketCodec.of(SecrecyConfigS2CPacket::write, SecrecyConfigS2CPacket::new);

    private SecrecyConfigS2CPacket(RegistryByteBuf buf) {
        this(buf.readString());
    }

    private void write(RegistryByteBuf buf) {
        buf.writeString(json);
    }

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
