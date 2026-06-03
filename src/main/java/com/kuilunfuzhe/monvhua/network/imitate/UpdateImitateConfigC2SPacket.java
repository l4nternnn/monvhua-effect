package com.kuilunfuzhe.monvhua.network.imitate;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record UpdateImitateConfigC2SPacket(String json) implements CustomPayload {
    public static final Id<UpdateImitateConfigC2SPacket> ID = new Id<>(Identifier.of("monvhua", "update_imitate_config"));
    public static final PacketCodec<RegistryByteBuf, UpdateImitateConfigC2SPacket> CODEC = PacketCodec.of(UpdateImitateConfigC2SPacket::write, UpdateImitateConfigC2SPacket::new);

    private UpdateImitateConfigC2SPacket(RegistryByteBuf buf) {
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