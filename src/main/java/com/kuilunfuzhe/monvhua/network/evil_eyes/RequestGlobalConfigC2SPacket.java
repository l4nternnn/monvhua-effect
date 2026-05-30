package com.kuilunfuzhe.monvhua.network.evil_eyes;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** 客户端→服务端：请求获取全局配置。客户端启动或重新连接时发送，服务端回复 {@link GlobalConfigS2CPacket}。 */
public record RequestGlobalConfigC2SPacket() implements CustomPayload {
    public static final Id<RequestGlobalConfigC2SPacket> ID = new Id<>(Identifier.of("monvhua", "request_global_config"));
    public static final PacketCodec<RegistryByteBuf, RequestGlobalConfigC2SPacket> CODEC = PacketCodec.unit(new RequestGlobalConfigC2SPacket());



    private static boolean registered = false;

    /** 注册数据包类型到 C2S 载荷注册表（幂等） */
    public static void register() {
        if (!registered) {

            PayloadTypeRegistry.playC2S().register(ID, CODEC);
            registered = true;
        }
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}