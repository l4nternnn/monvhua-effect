package com.kuilunfuzhe.monvhua.network.secrecy;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 客户端 -> 服务端：请求隐身配置。
 * 客户端初始化时发送此空包，向服务端请求当前玩家的隐身功能配置数据。
 */
public record RequestSecrecyConfigC2SPacket() implements CustomPayload {
    public static final Id<RequestSecrecyConfigC2SPacket> ID = new Id<>(Identifier.of("monvhua", "request_secrecy_config"));
    public static final PacketCodec<RegistryByteBuf, RequestSecrecyConfigC2SPacket> CODEC = PacketCodec.unit(new RequestSecrecyConfigC2SPacket());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    private static boolean registered = false;

    /**
     * 注册此数据包到 C2S 负载类型注册表。
     */
    public static void register() {
        if (!registered) {
            PayloadTypeRegistry.playC2S().register(ID, CODEC);
            registered = true;
        }
    }
}
