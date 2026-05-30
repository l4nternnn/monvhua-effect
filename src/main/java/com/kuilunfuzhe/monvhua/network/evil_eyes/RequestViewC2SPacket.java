// 文件：RequestViewC2SPacket.java
package com.kuilunfuzhe.monvhua.network.evil_eyes;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * 客户端→服务端：请求切换到指定实体的观察视角。
 * 客户端在玩家选择观察目标时发送，服务端验证权限后通过 {@link SetCameraEntityS2CPacket} 响应。
 */
public record RequestViewC2SPacket(UUID entityUuid) implements CustomPayload {
    public static final Id<RequestViewC2SPacket> ID = new Id<>(Identifier.of("monvhua", "request_view"));
    public static final PacketCodec<RegistryByteBuf, RequestViewC2SPacket> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING.xmap(UUID::fromString, UUID::toString), RequestViewC2SPacket::entityUuid,
            RequestViewC2SPacket::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}