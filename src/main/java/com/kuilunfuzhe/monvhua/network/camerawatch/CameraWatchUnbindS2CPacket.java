package com.kuilunfuzhe.monvhua.network.camerawatch;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 服务端 -> 客户端：摄像头观察解绑。
 * 服务端通知客户端观察会话已结束（目标断开、死亡或主动取消），客户端应停止渲染远程视角。
 */
public record CameraWatchUnbindS2CPacket() implements CustomPayload {
    public static final Id<CameraWatchUnbindS2CPacket> ID =
            new Id<>(Identifier.of("monvhua", "camera_watch_unbind"));
    public static final PacketCodec<PacketByteBuf, CameraWatchUnbindS2CPacket> CODEC =
            PacketCodec.unit(new CameraWatchUnbindS2CPacket());

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