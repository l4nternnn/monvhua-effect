package com.kuilunfuzhe.monvhua.network.imitate;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SoundWavePacket() implements CustomPayload {

    public static final Id<SoundWavePacket> ID = new Id<>(Identifier.of("monvhua", "trigger_sound_wave"));
    public static final PacketCodec<RegistryByteBuf, SoundWavePacket> CODEC = PacketCodec.unit(new SoundWavePacket());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    private static boolean registered = false;

    public static void register() {
        if (!registered) {
            net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S().register(ID, CODEC);
            registered = true;
        }
    }

    public static void registerHandler() {
        ServerPlayNetworking.registerGlobalReceiver(ID, (packet, context) -> {
            context.server().execute(() -> {
                SoundWaveEffect.execute(context.player());
            });
        });
    }
}