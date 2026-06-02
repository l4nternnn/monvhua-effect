package com.kuilunfuzhe.monvhua.network.action;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public record PreviewTimelineResultS2CPacket(List<PreviewEntry> entries) implements CustomPayload {
    public record PreviewEntry(int second, String actionId, String previewText, String actionType) {}

    public static final Id<PreviewTimelineResultS2CPacket> ID = new Id<>(Identifier.of("monvhua", "preview_timeline_result"));
    public static final PacketCodec<RegistryByteBuf, PreviewTimelineResultS2CPacket> CODEC = PacketCodec.of(
            PreviewTimelineResultS2CPacket::write, PreviewTimelineResultS2CPacket::new);

    private PreviewTimelineResultS2CPacket(RegistryByteBuf buf) {
        this(readEntries(buf));
    }

    private static List<PreviewEntry> readEntries(RegistryByteBuf buf) {
        int size = buf.readVarInt();
        List<PreviewEntry> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(new PreviewEntry(buf.readVarInt(), buf.readString(), buf.readString(), buf.readString()));
        }
        return list;
    }

    private void write(RegistryByteBuf buf) {
        buf.writeVarInt(entries.size());
        for (PreviewEntry e : entries) {
            buf.writeVarInt(e.second);
            buf.writeString(e.actionId);
            buf.writeString(e.previewText);
            buf.writeString(e.actionType);
        }
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }

    private static boolean registered = false;
    public static void register() {
        if (!registered) { PayloadTypeRegistry.playS2C().register(ID, CODEC); registered = true; }
    }
}
