package com.kuilunfuzhe.monvhua.network.action;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record PreviewResultS2CPacket(String actionId, String previewText) implements CustomPayload {
    public static final Id<PreviewResultS2CPacket> ID = new Id<>(Identifier.of("monvhua", "preview_result"));
    public static final PacketCodec<RegistryByteBuf, PreviewResultS2CPacket> CODEC = PacketCodec.of(PreviewResultS2CPacket::write, PreviewResultS2CPacket::new);

    private PreviewResultS2CPacket(RegistryByteBuf buf) { this(buf.readString(), buf.readString()); }
    private void write(RegistryByteBuf buf) { buf.writeString(actionId); buf.writeString(previewText); }

    @Override public Id<? extends CustomPayload> getId() { return ID; }

    private static boolean registered = false;
    public static void register() {
        if (!registered) { PayloadTypeRegistry.playS2C().register(ID, CODEC); registered = true; }
    }
}
