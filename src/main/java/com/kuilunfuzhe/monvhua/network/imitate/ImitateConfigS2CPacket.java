package com.kuilunfuzhe.monvhua.network.imitate;

import com.kuilunfuzhe.monvhua.item.config.ImitateConfig;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ImitateConfigS2CPacket(String json) implements CustomPayload {
    public static final Id<ImitateConfigS2CPacket> ID = new Id<>(Identifier.of("monvhua", "imitate_config"));
    public static final PacketCodec<RegistryByteBuf, ImitateConfigS2CPacket> CODEC = PacketCodec.of(ImitateConfigS2CPacket::write, ImitateConfigS2CPacket::new);

    private ImitateConfigS2CPacket(RegistryByteBuf buf) {
        this(buf.readString());
    }

    private void write(RegistryByteBuf buf) {
        buf.writeString(json);
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

    public ImitateConfig getConfig() {
        return ImitateConfig.fromJson(json);
    }
}