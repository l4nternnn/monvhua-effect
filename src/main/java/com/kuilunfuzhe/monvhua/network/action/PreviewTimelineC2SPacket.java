package com.kuilunfuzhe.monvhua.network.action;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record PreviewTimelineC2SPacket() implements CustomPayload {
    public static final Id<PreviewTimelineC2SPacket> ID = new Id<>(Identifier.of("monvhua", "preview_timeline"));
    public static final PacketCodec<RegistryByteBuf, PreviewTimelineC2SPacket> CODEC = PacketCodec.of(
            PreviewTimelineC2SPacket::write, PreviewTimelineC2SPacket::new);

    private PreviewTimelineC2SPacket(RegistryByteBuf buf) { this(); }
    private void write(RegistryByteBuf buf) {}

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }

    private static boolean registered = false;
    public static void register() {
        if (!registered) { PayloadTypeRegistry.playC2S().register(ID, CODEC); registered = true; }
    }
}
