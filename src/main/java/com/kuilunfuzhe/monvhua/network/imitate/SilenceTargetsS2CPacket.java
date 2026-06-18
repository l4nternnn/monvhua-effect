package com.kuilunfuzhe.monvhua.network.imitate;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record SilenceTargetsS2CPacket(double radius, List<Entry> entries) implements CustomPayload {
    public static final Id<SilenceTargetsS2CPacket> ID = new Id<>(Identifier.of("monvhua", "silence_targets"));
    public static final PacketCodec<RegistryByteBuf, SilenceTargetsS2CPacket> CODEC = PacketCodec.of(SilenceTargetsS2CPacket::write, SilenceTargetsS2CPacket::new);

    public record Entry(UUID uuid, String name, String tag, double distance) {
    }

    private static boolean registered = false;

    private SilenceTargetsS2CPacket(RegistryByteBuf buf) {
        this(buf.readDouble(), readEntries(buf));
    }

    private void write(RegistryByteBuf buf) {
        buf.writeDouble(radius);
        buf.writeVarInt(entries.size());
        for (Entry entry : entries) {
            buf.writeUuid(entry.uuid());
            buf.writeString(entry.name() == null ? "" : entry.name());
            buf.writeString(entry.tag() == null ? "" : entry.tag());
            buf.writeDouble(entry.distance());
        }
    }

    private static List<Entry> readEntries(RegistryByteBuf buf) {
        int size = buf.readVarInt();
        List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new Entry(buf.readUuid(), buf.readString(), buf.readString(), buf.readDouble()));
        }
        return entries;
    }

    public static void register() {
        if (!registered) {
            PayloadTypeRegistry.playS2C().register(ID, CODEC);
            registered = true;
        }
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
