package com.kuilunfuzhe.monvhua.network.action;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record TimelineControlC2SPacket(String action, int second, String actionId) implements CustomPayload {
    public static final Id<TimelineControlC2SPacket> ID = new Id<>(Identifier.of("monvhua", "timeline_control"));
    public static final PacketCodec<RegistryByteBuf, TimelineControlC2SPacket> CODEC = PacketCodec.of(
            TimelineControlC2SPacket::write, TimelineControlC2SPacket::new);

    private TimelineControlC2SPacket(RegistryByteBuf buf) {
        this(buf.readString(), buf.readVarInt(), buf.readString());
    }

    private void write(RegistryByteBuf buf) {
        buf.writeString(action);
        buf.writeVarInt(second);
        buf.writeString(actionId != null ? actionId : "");
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }

    private static boolean registered = false;
    public static void register() {
        if (!registered) { PayloadTypeRegistry.playC2S().register(ID, CODEC); registered = true; }
    }
}
