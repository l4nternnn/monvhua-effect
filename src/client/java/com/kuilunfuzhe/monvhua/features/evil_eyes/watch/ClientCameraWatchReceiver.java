package com.kuilunfuzhe.monvhua.features.evil_eyes.watch;

import com.kuilunfuzhe.monvhua.network.camerawatch.CameraUpdateS2CPacket;
import com.kuilunfuzhe.monvhua.network.camerawatch.CameraWatchUnbindS2CPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * 客户端摄像机观看网络包接收器。
 * 注册两个网络包接收器：{@link CameraUpdateS2CPacket} 用于接收服务器推送的摄像机位置/朝向更新，
 * {@link CameraWatchUnbindS2CPacket} 用于接收解除绑定信号。
 */
public class ClientCameraWatchReceiver {
    /** 注册所有摄像机观看相关的客户端网络包接收器 */
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(CameraUpdateS2CPacket.ID, (packet, context) -> {
            context.client().execute(() ->
                    CameraWatchClientHandler.onCameraUpdate(packet.pos(), packet.yaw(), packet.pitch())
            );
        });

        ClientPlayNetworking.registerGlobalReceiver(CameraWatchUnbindS2CPacket.ID, (packet, context) -> {
            context.client().execute(CameraWatchClientHandler::onUnbind);
        });
    }
}