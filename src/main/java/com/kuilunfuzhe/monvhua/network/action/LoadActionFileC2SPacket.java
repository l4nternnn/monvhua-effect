package com.kuilunfuzhe.monvhua.network.action;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record LoadActionFileC2SPacket(String filename) implements CustomPayload {
    public static final Id<LoadActionFileC2SPacket> ID = new Id<>(Identifier.of("monvhua", "load_action_file"));
    public static final PacketCodec<RegistryByteBuf, LoadActionFileC2SPacket> CODEC = PacketCodec.of(LoadActionFileC2SPacket::write, LoadActionFileC2SPacket::new);

    private LoadActionFileC2SPacket(RegistryByteBuf buf) { this(buf.readString()); }
    private void write(RegistryByteBuf buf) { buf.writeString(filename); }

    @Override public Id<? extends CustomPayload> getId() { return ID; }

    private static boolean registered = false;
    public static void register() {
        if (!registered) { PayloadTypeRegistry.playC2S().register(ID, CODEC); registered = true; }
    }
}
