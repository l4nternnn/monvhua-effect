package com.kuilunfuzhe.monvhua.network.floating;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record FloatingEnergySyncS2CPacket(double currentEnergy, double maxEnergy) implements CustomPayload {
    public static final Id<FloatingEnergySyncS2CPacket> ID = new Id<>(Identifier.of("monvhua", "floating_energy_sync"));
    public static final PacketCodec<RegistryByteBuf, FloatingEnergySyncS2CPacket> CODEC = PacketCodec.of(
            FloatingEnergySyncS2CPacket::write,
            FloatingEnergySyncS2CPacket::new
    );

    private static boolean registered = false;

    private FloatingEnergySyncS2CPacket(RegistryByteBuf buf) {
        this(buf.readDouble(), buf.readDouble());
    }

    private void write(RegistryByteBuf buf) {
        buf.writeDouble(currentEnergy);
        buf.writeDouble(maxEnergy);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public static void register() {
        if (!registered) {
            PayloadTypeRegistry.playS2C().register(ID, CODEC);
            registered = true;
        }
    }
}
