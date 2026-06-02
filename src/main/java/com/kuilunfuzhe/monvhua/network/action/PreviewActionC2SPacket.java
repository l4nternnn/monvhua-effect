package com.kuilunfuzhe.monvhua.network.action;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record PreviewActionC2SPacket(String actionJson) implements CustomPayload {
    public static final Id<PreviewActionC2SPacket> ID = new Id<>(Identifier.of("monvhua", "preview_action"));
    public static final PacketCodec<RegistryByteBuf, PreviewActionC2SPacket> CODEC = PacketCodec.of(PreviewActionC2SPacket::write, PreviewActionC2SPacket::new);

    private PreviewActionC2SPacket(RegistryByteBuf buf) { this(buf.readString()); }
    private void write(RegistryByteBuf buf) { buf.writeString(actionJson); }

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
