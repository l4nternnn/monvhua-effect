package com.kuilunfuzhe.monvhua.network.gazeguidance;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public record MarkedListPacket(List<Entry> entries) implements CustomPayload {
    public static final Id<MarkedListPacket> ID = new Id<>(Identifier.of("monvhua", "guidance_marked_list"));
    public static final PacketCodec<RegistryByteBuf, MarkedListPacket> CODEC = PacketCodec.of(MarkedListPacket::write, MarkedListPacket::new);
    private static boolean registered = false;

    public record Entry(String name, String tag) {
    }

    private MarkedListPacket(RegistryByteBuf buf) {
        this(readEntries(buf));
    }

    private void write(RegistryByteBuf buf) {
        buf.writeVarInt(entries.size());
        for (Entry entry : entries) {
            buf.writeString(entry.name() == null ? "" : entry.name());
            buf.writeString(entry.tag() == null ? "" : entry.tag());
        }
    }

    private static List<Entry> readEntries(RegistryByteBuf buf) {
        int size = buf.readVarInt();
        List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new Entry(buf.readString(), buf.readString()));
        }
        return entries;
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public static void register() {
        if (!registered) {
            net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C().register(ID, CODEC);
            registered = true;
        }
    }
}
