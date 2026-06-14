package com.kuilunfuzhe.monvhua.network.gazeguidance;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public record MarkedListPacket(List<String> names) implements CustomPayload {
    public static final Id<MarkedListPacket> ID = new Id<>(Identifier.of("monvhua", "guidance_marked_list"));
    public static final PacketCodec<RegistryByteBuf, MarkedListPacket> CODEC = PacketCodec.of(MarkedListPacket::write, MarkedListPacket::new);
    private static boolean registered = false;

    private MarkedListPacket(RegistryByteBuf buf) {
        this(readNames(buf));
    }

    private void write(RegistryByteBuf buf) {
        buf.writeVarInt(names.size());
        for (String name : names) {
            buf.writeString(name);
        }
    }

    private static List<String> readNames(RegistryByteBuf buf) {
        int size = buf.readVarInt();
        List<String> names = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            names.add(buf.readString());
        }
        return names;
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public static void register() {
        if (!registered) {
            net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C().register(ID, CODEC);
            registered = true;
        }
    }
}
