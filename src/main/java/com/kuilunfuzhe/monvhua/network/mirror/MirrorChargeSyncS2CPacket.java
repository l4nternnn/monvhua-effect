package com.kuilunfuzhe.monvhua.network.mirror;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record MirrorChargeSyncS2CPacket(int currentTicks, int maxTicks) implements CustomPayload {
    public static final Id<MirrorChargeSyncS2CPacket> ID = new Id<>(Identifier.of("monvhua", "mirror_charge_sync"));
    public static final PacketCodec<RegistryByteBuf, MirrorChargeSyncS2CPacket> CODEC = PacketCodec.of(
            MirrorChargeSyncS2CPacket::write, MirrorChargeSyncS2CPacket::new
    );

    private MirrorChargeSyncS2CPacket(RegistryByteBuf buf) {
        this(buf.readInt(), buf.readInt());
    }

    private void write(RegistryByteBuf buf) {
        buf.writeInt(currentTicks);
        buf.writeInt(maxTicks);
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
