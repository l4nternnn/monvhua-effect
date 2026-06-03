package com.kuilunfuzhe.monvhua.network.evil_eyes;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** 服务端→客户端：通知客户端打开邪恶之眼 UI 界面。 */
public record OpenUIPacket() implements CustomPayload {
    public static final Id<OpenUIPacket> ID = new Id<>(Identifier.of("monvhua", "open_ui"));
    public static final PacketCodec<RegistryByteBuf, OpenUIPacket> CODEC = PacketCodec.unit(new OpenUIPacket());


    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    private static boolean registered = false;

    /** 注册数据包类型到 S2C 载荷注册表（幂等） */
    public static void register() {
        if (!registered) {
            net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C().register(ID, CODEC);
            registered = true;
        }
    }
}