package com.kuilunfuzhe.monvhua.network.imitate;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ResetCooldownsS2CPacket() implements CustomPayload {
    public static final Id<ResetCooldownsS2CPacket> ID = new Id<>(Identifier.of("monvhua", "reset_cooldowns"));
    public static final PacketCodec<RegistryByteBuf, ResetCooldownsS2CPacket> CODEC = PacketCodec.of(ResetCooldownsS2CPacket::write, ResetCooldownsS2CPacket::new);

    private ResetCooldownsS2CPacket(RegistryByteBuf buf) {
        this();
    }

    private void write(RegistryByteBuf buf) {
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