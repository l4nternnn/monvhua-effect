package com.kuilunfuzhe.monvhua.network.imitate;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record RequestSilenceTargetsC2SPacket(double radius) implements CustomPayload {
    public static final Id<RequestSilenceTargetsC2SPacket> ID = new Id<>(Identifier.of("monvhua", "request_silence_targets"));
    public static final PacketCodec<RegistryByteBuf, RequestSilenceTargetsC2SPacket> CODEC = PacketCodec.tuple(
            PacketCodecs.DOUBLE, RequestSilenceTargetsC2SPacket::radius,
            RequestSilenceTargetsC2SPacket::new
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
