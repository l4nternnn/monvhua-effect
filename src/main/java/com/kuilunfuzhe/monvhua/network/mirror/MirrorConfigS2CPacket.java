package com.kuilunfuzhe.monvhua.network.mirror;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record MirrorConfigS2CPacket(String json) implements CustomPayload {
    public static final Id<MirrorConfigS2CPacket> ID = new Id<>(Identifier.of("monvhua", "mirror_config_sync"));
    public static final PacketCodec<RegistryByteBuf, MirrorConfigS2CPacket> CODEC = PacketCodec.of(MirrorConfigS2CPacket::write, MirrorConfigS2CPacket::new);

    private MirrorConfigS2CPacket(RegistryByteBuf buf) {
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
