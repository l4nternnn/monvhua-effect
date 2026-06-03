package com.kuilunfuzhe.monvhua.network.action;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record TimelineStateS2CPacket(int currentSecond, boolean running, boolean paused, boolean loop, int totalSeconds) implements CustomPayload {
    public static final Id<TimelineStateS2CPacket> ID = new Id<>(Identifier.of("monvhua", "timeline_state"));
    public static final PacketCodec<RegistryByteBuf, TimelineStateS2CPacket> CODEC = PacketCodec.of(
            TimelineStateS2CPacket::write, TimelineStateS2CPacket::new);

    private TimelineStateS2CPacket(RegistryByteBuf buf) {
        this(buf.readVarInt(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt());
    }

    private void write(RegistryByteBuf buf) {
        buf.writeVarInt(currentSecond);
        buf.writeBoolean(running);
        buf.writeBoolean(paused);
        buf.writeBoolean(loop);
        buf.writeVarInt(totalSeconds);
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }

    private static boolean registered = false;
    public static void register() {
        if (!registered) { PayloadTypeRegistry.playS2C().register(ID, CODEC); registered = true; }
    }
}
