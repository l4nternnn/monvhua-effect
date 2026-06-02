package com.kuilunfuzhe.monvhua.network.action;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ListActionFilesC2SPacket() implements CustomPayload {
    public static final Id<ListActionFilesC2SPacket> ID = new Id<>(Identifier.of("monvhua", "list_action_files"));
    public static final PacketCodec<RegistryByteBuf, ListActionFilesC2SPacket> CODEC = PacketCodec.unit(new ListActionFilesC2SPacket());

    @Override public Id<? extends CustomPayload> getId() { return ID; }

    private static boolean registered = false;
    public static void register() {
        if (!registered) { PayloadTypeRegistry.playC2S().register(ID, CODEC); registered = true; }
    }
}
