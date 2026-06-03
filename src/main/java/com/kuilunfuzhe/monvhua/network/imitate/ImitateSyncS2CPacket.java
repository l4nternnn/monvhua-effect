package com.kuilunfuzhe.monvhua.network.imitate;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ImitateSyncS2CPacket(String roleName, long endTime, long switchCooldownEnd, long soundWaveCooldownEnd) implements CustomPayload {
    public static final Id<ImitateSyncS2CPacket> ID = new Id<>(Identifier.of("monvhua", "imitate_sync"));
    public static final PacketCodec<RegistryByteBuf, ImitateSyncS2CPacket> CODEC = PacketCodec.of(ImitateSyncS2CPacket::write, ImitateSyncS2CPacket::new);

    private ImitateSyncS2CPacket(RegistryByteBuf buf) {
        this(buf.readString(), buf.readLong(), buf.readLong(), buf.readLong());
    }

    private void write(RegistryByteBuf buf) {
        buf.writeString(roleName != null ? roleName : "");
        buf.writeLong(endTime);
        buf.writeLong(switchCooldownEnd);
        buf.writeLong(soundWaveCooldownEnd);
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