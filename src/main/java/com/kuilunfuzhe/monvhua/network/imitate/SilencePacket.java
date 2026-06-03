package com.kuilunfuzhe.monvhua.network.imitate;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

public record SilencePacket(UUID targetUUID) implements CustomPayload {
    public static final Id<SilencePacket> ID = new Id<>(Identifier.of("monvhua", "silence"));

    public static final PacketCodec<RegistryByteBuf, SilencePacket> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING.xmap(UUID::fromString, UUID::toString), SilencePacket::targetUUID,
            SilencePacket::new
    );

    private static boolean registered = false;

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