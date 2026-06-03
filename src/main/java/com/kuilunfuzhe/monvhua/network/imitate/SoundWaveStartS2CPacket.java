package com.kuilunfuzhe.monvhua.network.imitate;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SoundWaveStartS2CPacket(double x, double y, double z, double maxRadius) implements CustomPayload {

    public static final Id<SoundWaveStartS2CPacket> ID = new Id<>(Identifier.of("monvhua", "sound_wave_start"));
    public static final PacketCodec<RegistryByteBuf, SoundWaveStartS2CPacket> CODEC = PacketCodec.tuple(
            PacketCodecs.DOUBLE, SoundWaveStartS2CPacket::x,
            PacketCodecs.DOUBLE, SoundWaveStartS2CPacket::y,
            PacketCodecs.DOUBLE, SoundWaveStartS2CPacket::z,
            PacketCodecs.DOUBLE, SoundWaveStartS2CPacket::maxRadius,
            SoundWaveStartS2CPacket::new
    );

    private static boolean registered = false;

    public static void register() {
        if (!registered) {
            net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C().register(ID, CODEC);
            registered = true;
        }
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}