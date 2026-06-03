package com.kuilunfuzhe.monvhua.network.imitate;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

public record SilenceEffectS2CPacket(UUID targetUUID, int durationSeconds) implements CustomPayload {
    public static final Id<SilenceEffectS2CPacket> ID = new Id<>(Identifier.of("monvhua", "silence_effect"));

    public static final PacketCodec<RegistryByteBuf, SilenceEffectS2CPacket> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING.xmap(UUID::fromString, UUID::toString), SilenceEffectS2CPacket::targetUUID,
            PacketCodecs.VAR_INT, SilenceEffectS2CPacket::durationSeconds,
            SilenceEffectS2CPacket::new
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