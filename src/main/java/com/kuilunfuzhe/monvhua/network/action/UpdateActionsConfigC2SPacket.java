package com.kuilunfuzhe.monvhua.network.action;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record UpdateActionsConfigC2SPacket(String json) implements CustomPayload {
    public static final Id<UpdateActionsConfigC2SPacket> ID = new Id<>(Identifier.of("monvhua", "update_actions_config"));
    public static final PacketCodec<RegistryByteBuf, UpdateActionsConfigC2SPacket> CODEC = PacketCodec.of(UpdateActionsConfigC2SPacket::write, UpdateActionsConfigC2SPacket::new);

    private UpdateActionsConfigC2SPacket(RegistryByteBuf buf) { this(buf.readString()); }
    private void write(RegistryByteBuf buf) { buf.writeString(json); }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }

    private static boolean registered = false;
    public static void register() {
        if (!registered) {
            PayloadTypeRegistry.playC2S().register(ID, CODEC);
            registered = true;
        }
    }
}
