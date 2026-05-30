package com.kuilunfuzhe.monvhua.network.evil_eyes;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** 载荷定义：通知客户端打开 UI 界面（备用版本，与 {@link OpenUIPacket} 功能相同，使用不同的 ID 类型）。 */
public record OpenUIPayload() implements CustomPayload {
    public static final CustomPayload.Id<OpenUIPayload> ID = new CustomPayload.Id<>(Identifier.of("monvhua", "open_ui"));
    public static final PacketCodec<RegistryByteBuf, OpenUIPayload> CODEC = PacketCodec.unit(new OpenUIPayload());



    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}