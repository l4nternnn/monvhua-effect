package com.kuilunfuzhe.monvhua.network.secrecy;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SecrecyStateS2CPacket(boolean invisible, int fadeOutTicks) implements CustomPayload {
    public static final Id<SecrecyStateS2CPacket> ID = new Id<>(Identifier.of("monvhua", "secrecy_state"));
    public static final PacketCodec<RegistryByteBuf, SecrecyStateS2CPacket> CODEC = PacketCodec.of(SecrecyStateS2CPacket::write, SecrecyStateS2CPacket::new);

    private SecrecyStateS2CPacket(RegistryByteBuf buf) {
        this(buf.readBoolean(), buf.readVarInt());
    }

    private void write(RegistryByteBuf buf) {
        buf.writeBoolean(invisible);
        buf.writeVarInt(fadeOutTicks);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    private static boolean registered = false;

    public static void register() {
        if (!registered) {
            PayloadTypeRegistry.playS2C().register(ID, CODEC);
            registered = true;
        }
    }
}
