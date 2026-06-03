package com.kuilunfuzhe.monvhua.network.action;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ActionsConfigS2CPacket(String json) implements CustomPayload {
    public static final Id<ActionsConfigS2CPacket> ID = new Id<>(Identifier.of("monvhua", "actions_config_sync"));
    public static final PacketCodec<RegistryByteBuf, ActionsConfigS2CPacket> CODEC = PacketCodec.of(ActionsConfigS2CPacket::write, ActionsConfigS2CPacket::new);

    private ActionsConfigS2CPacket(RegistryByteBuf buf) { this(buf.readString()); }
    private void write(RegistryByteBuf buf) { buf.writeString(json); }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }

    private static boolean registered = false;
    public static void register() {
        if (!registered) {
            PayloadTypeRegistry.playS2C().register(ID, CODEC);
            registered = true;
        }
    }
}
