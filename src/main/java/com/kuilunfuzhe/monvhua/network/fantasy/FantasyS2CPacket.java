package com.kuilunfuzhe.monvhua.network.fantasy;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record FantasyS2CPacket(String text, int durationTicks, boolean isBurst, boolean isGlitch, String colorCode) implements CustomPayload {
    public static final CustomPayload.Id<FantasyS2CPacket> ID =
            new CustomPayload.Id<>(Identifier.of("monvhua", "fantasy_screen_text"));

    public static final PacketCodec<RegistryByteBuf, FantasyS2CPacket> CODEC = PacketCodec.of(
            FantasyS2CPacket::write,
            FantasyS2CPacket::new
    );

    private static boolean registered = false;

    private FantasyS2CPacket(RegistryByteBuf buf) {
        this(buf.readString(), buf.readInt(), buf.readBoolean(), buf.readBoolean(), buf.readString());
    }

    private void write(RegistryByteBuf buf) {
        buf.writeString(text);
        buf.writeInt(durationTicks);
        buf.writeBoolean(isBurst);
        buf.writeBoolean(isGlitch);
        buf.writeString(colorCode);
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