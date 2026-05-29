package com.kuilunfuzhe.monvhua.network.mirror;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record MirrorChargeC2SPacket(boolean start) implements CustomPayload {
    public static final Id<MirrorChargeC2SPacket> ID = new Id<>(Identifier.of("monvhua", "mirror_charge"));
    public static final PacketCodec<RegistryByteBuf, MirrorChargeC2SPacket> CODEC = PacketCodec.tuple(
            PacketCodecs.BOOLEAN, MirrorChargeC2SPacket::start,
            MirrorChargeC2SPacket::new
    );
    private static boolean registered = false;

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public static void register() {
        if (!registered) {
            PayloadTypeRegistry.playC2S().register(ID, CODEC);
            registered = true;
        }
    }
}
