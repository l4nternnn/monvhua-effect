package com.kuilunfuzhe.monvhua.network.hold_hands;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record HoldHandsInteractC2SPacket(int entityId) implements CustomPayload {
    public static final CustomPayload.Id<HoldHandsInteractC2SPacket> ID =
            new CustomPayload.Id<>(Identifier.of("monvhua", "hold_hands_interact"));

    public static final PacketCodec<RegistryByteBuf, HoldHandsInteractC2SPacket> CODEC =
            PacketCodec.tuple(PacketCodecs.INTEGER, HoldHandsInteractC2SPacket::entityId, HoldHandsInteractC2SPacket::new);

    private static boolean registered;

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
