package com.kuilunfuzhe.monvhua.network.camerawatch;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 服务端 -> 客户端：摄像头观察绑定。
 * 服务端通知客户端已成功绑定到指定实体，客户端开始接收该实体的视角更新。
 */
public record CameraWatchBindS2CPacket(int entityId) implements CustomPayload {
    public static final Id<CameraWatchBindS2CPacket> ID =
            new Id<>(Identifier.of("monvhua", "camera_watch_bind"));
    public static final PacketCodec<PacketByteBuf, CameraWatchBindS2CPacket> CODEC =
            PacketCodec.tuple(PacketCodecs.INTEGER, CameraWatchBindS2CPacket::entityId, CameraWatchBindS2CPacket::new);

    /**
     * 注册此数据包到 S2C 负载类型注册表。
     */
    private static boolean registered = false;
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