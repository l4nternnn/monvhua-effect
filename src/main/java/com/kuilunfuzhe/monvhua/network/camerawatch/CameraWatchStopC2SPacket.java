package com.kuilunfuzhe.monvhua.network.camerawatch;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 客户端 -> 服务端：请求停止摄像头观察。
 * 客户端发送空包，请求停止当前正在进行的远程视角观察。
 */
public record CameraWatchStopC2SPacket() implements CustomPayload {
    public static final Id<CameraWatchStopC2SPacket> ID =
            new Id<>(Identifier.of("monvhua", "camera_watch_stop"));
    public static final PacketCodec<PacketByteBuf, CameraWatchStopC2SPacket> CODEC =
            PacketCodec.unit(new CameraWatchStopC2SPacket());


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

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}