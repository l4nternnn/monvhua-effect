package com.kuilunfuzhe.monvhua.network.secrecy;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record RequestSecrecyConfigC2SPacket() implements CustomPayload {
    public static final Id<RequestSecrecyConfigC2SPacket> ID = new Id<>(Identifier.of("monvhua", "request_secrecy_config"));
    public static final PacketCodec<RegistryByteBuf, RequestSecrecyConfigC2SPacket> CODEC = PacketCodec.unit(new RequestSecrecyConfigC2SPacket());

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
