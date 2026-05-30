package com.kuilunfuzhe.monvhua.network.camerawatch;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * 服务端 -> 客户端：摄像头观察位置更新。
 * 服务端将绑定实体的当前位置和视角同步给客户端，用于客户端渲染远程观察画面。
 */
public record CameraUpdateS2CPacket(Vec3d pos, float yaw, float pitch) implements CustomPayload {
    public static final Id<CameraUpdateS2CPacket> ID = new Id<>(Identifier.of("monvhua", "camera_update"));
    public static final PacketCodec<PacketByteBuf, CameraUpdateS2CPacket> CODEC = PacketCodec.tuple(
            Vec3d.PACKET_CODEC, CameraUpdateS2CPacket::pos,
            PacketCodecs.FLOAT, CameraUpdateS2CPacket::yaw,
            PacketCodecs.FLOAT, CameraUpdateS2CPacket::pitch,
            CameraUpdateS2CPacket::new
    );


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
    public Id<? extends CustomPayload> getId() { return ID; }
}