// 文件：SetCameraEntityS2CPacket.java
package com.kuilunfuzhe.monvhua.network.evil_eyes;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * 服务端→客户端：将玩家观察视角切换到指定实体。
 * 服务端确认观察请求后发送，客户端将摄像机绑定到目标实体。
 */
public record SetCameraEntityS2CPacket(UUID cameraEntityUuid) implements CustomPayload {
    public static final Id<SetCameraEntityS2CPacket> ID = new Id<>(Identifier.of("monvhua", "set_camera"));
    public static final PacketCodec<RegistryByteBuf, SetCameraEntityS2CPacket> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING.xmap(UUID::fromString, UUID::toString), SetCameraEntityS2CPacket::cameraEntityUuid,
            SetCameraEntityS2CPacket::new
    );

    private static boolean registered = false;
    /** 注册数据包类型到 S2C 载荷注册表（幂等） */
    public static void register() {
        if (!registered) {
            net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C().register(ID, CODEC);
            registered = true;
        }
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}