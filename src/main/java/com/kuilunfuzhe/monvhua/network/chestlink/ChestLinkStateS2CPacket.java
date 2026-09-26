package com.kuilunfuzhe.monvhua.network.chestlink;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ChestLinkStateS2CPacket(boolean active, String dimension, int x, int y, int z) implements CustomPayload {
    public static final Id<ChestLinkStateS2CPacket> ID = new Id<>(Identifier.of("monvhua", "chest_link_state"));
    public static final PacketCodec<RegistryByteBuf, ChestLinkStateS2CPacket> CODEC = PacketCodec.of(ChestLinkStateS2CPacket::write, ChestLinkStateS2CPacket::new);
    private ChestLinkStateS2CPacket(RegistryByteBuf buf) { this(buf.readBoolean(), buf.readString(), buf.readInt(), buf.readInt(), buf.readInt()); }
    private void write(RegistryByteBuf buf) { buf.writeBoolean(active); buf.writeString(dimension); buf.writeInt(x); buf.writeInt(y); buf.writeInt(z); }
    @Override public Id<? extends CustomPayload> getId() { return ID; }
    private static boolean registered;
    public static void register() { if (!registered) { PayloadTypeRegistry.playS2C().register(ID, CODEC); registered = true; } }
}
