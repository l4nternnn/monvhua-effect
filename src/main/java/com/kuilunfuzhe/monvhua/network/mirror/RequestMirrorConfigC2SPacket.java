package com.kuilunfuzhe.monvhua.network.mirror;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 客户端 -> 服务端：请求镜子配置。
 * 客户端在初始化时发送此空包，向服务端请求当前玩家的镜子配置数据。
 */
public record RequestMirrorConfigC2SPacket() implements CustomPayload {
    public static final Id<RequestMirrorConfigC2SPacket> ID = new Id<>(Identifier.of("monvhua", "request_mirror_config"));
    public static final PacketCodec<RegistryByteBuf, RequestMirrorConfigC2SPacket> CODEC = PacketCodec.unit(new RequestMirrorConfigC2SPacket());

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
