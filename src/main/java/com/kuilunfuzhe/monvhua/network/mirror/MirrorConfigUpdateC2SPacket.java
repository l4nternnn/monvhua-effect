package com.kuilunfuzhe.monvhua.network.mirror;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record MirrorConfigUpdateC2SPacket(String json) implements CustomPayload {
    public static final Id<MirrorConfigUpdateC2SPacket> ID = new Id<>(Identifier.of("clairvoyance", "mirror_config_update"));
    public static final PacketCodec<RegistryByteBuf, MirrorConfigUpdateC2SPacket> CODEC = PacketCodec.of(MirrorConfigUpdateC2SPacket::write, MirrorConfigUpdateC2SPacket::new);

    private MirrorConfigUpdateC2SPacket(RegistryByteBuf buf) {
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
            PayloadTypeRegistry.playC2S().register(ID, CODEC);
            registered = true;
        }
    }
}
