package com.kuilunfuzhe.monvhua.network.imitate;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record PlayerStageS2CPacket(int stage) implements CustomPayload {

    public static final Id<PlayerStageS2CPacket> ID = new Id<>(Identifier.of("monvhua", "player_stage_imitate"));
    public static final PacketCodec<RegistryByteBuf, PlayerStageS2CPacket> CODEC = PacketCodec.tuple(
            PacketCodecs.INTEGER, PlayerStageS2CPacket::stage,
            PlayerStageS2CPacket::new
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
