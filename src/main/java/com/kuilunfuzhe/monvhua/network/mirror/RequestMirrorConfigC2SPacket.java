package com.kuilunfuzhe.monvhua.network.mirror;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record RequestMirrorConfigC2SPacket() implements CustomPayload {
    public static final Id<RequestMirrorConfigC2SPacket> ID = new Id<>(Identifier.of("monvhua", "request_mirror_config"));
    public static final PacketCodec<RegistryByteBuf, RequestMirrorConfigC2SPacket> CODEC = PacketCodec.unit(new RequestMirrorConfigC2SPacket());

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
