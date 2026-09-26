package com.kuilunfuzhe.monvhua.network.chestlink;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public record ChestLinkMappingsS2CPacket(boolean replace, List<Entry> entries) implements CustomPayload {
    public record Entry(String targetDimension, int targetX, int targetY, int targetZ,
                        String sourceDimension, int sourceX, int sourceY, int sourceZ) {}

    public static final Id<ChestLinkMappingsS2CPacket> ID = new Id<>(Identifier.of("monvhua", "chest_link_mappings"));
    public static final PacketCodec<RegistryByteBuf, ChestLinkMappingsS2CPacket> CODEC = PacketCodec.of(ChestLinkMappingsS2CPacket::write, ChestLinkMappingsS2CPacket::new);

    private ChestLinkMappingsS2CPacket(RegistryByteBuf buf) {
        this(buf.readBoolean(), readEntries(buf));
    }

    private void write(RegistryByteBuf buf) {
        buf.writeBoolean(replace);
        buf.writeVarInt(entries.size());
        for (Entry entry : entries) {
            buf.writeString(entry.targetDimension()); buf.writeInt(entry.targetX()); buf.writeInt(entry.targetY()); buf.writeInt(entry.targetZ());
            buf.writeString(entry.sourceDimension()); buf.writeInt(entry.sourceX()); buf.writeInt(entry.sourceY()); buf.writeInt(entry.sourceZ());
        }
    }

    private static List<Entry> readEntries(RegistryByteBuf buf) {
        int count = Math.min(buf.readVarInt(), 128);
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(new Entry(buf.readString(), buf.readInt(), buf.readInt(), buf.readInt(),
                    buf.readString(), buf.readInt(), buf.readInt(), buf.readInt()));
        }
        return entries;
    }

    @Override public Id<? extends CustomPayload> getId() { return ID; }

    private static boolean registered;
    public static void register() {
        if (!registered) {
            PayloadTypeRegistry.playS2C().register(ID, CODEC);
            registered = true;
        }
    }
}
