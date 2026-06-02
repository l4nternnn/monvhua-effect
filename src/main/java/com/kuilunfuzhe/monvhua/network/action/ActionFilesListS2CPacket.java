package com.kuilunfuzhe.monvhua.network.action;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import java.util.ArrayList;
import java.util.List;

public record ActionFilesListS2CPacket(List<String> files) implements CustomPayload {
    public static final Id<ActionFilesListS2CPacket> ID = new Id<>(Identifier.of("monvhua", "action_files_list"));
    public static final PacketCodec<RegistryByteBuf, ActionFilesListS2CPacket> CODEC = PacketCodec.of(ActionFilesListS2CPacket::write, ActionFilesListS2CPacket::new);

    private ActionFilesListS2CPacket(RegistryByteBuf buf) {
        this(new ArrayList<>());
        int count = buf.readVarInt();
        for (int i = 0; i < count; i++) files.add(buf.readString());
    }
    private void write(RegistryByteBuf buf) {
        buf.writeVarInt(files.size());
        for (String f : files) buf.writeString(f);
    }

    @Override public Id<? extends CustomPayload> getId() { return ID; }

    private static boolean registered = false;
    public static void register() {
        if (!registered) { PayloadTypeRegistry.playS2C().register(ID, CODEC); registered = true; }
    }
}
