package com.kuilunfuzhe.monvhua.network.evil_eyes;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 客户端→服务端：请求退出当前观察视角。
 * 客户端在玩家主动退出视角时发送，服务端收到后恢复其正常观察状态。
 */
public record ExitViewPayload() implements CustomPayload {
    public static final Id<ExitViewPayload> ID = new Id<>(Identifier.of("monvhua", "exit_view"));
    public static final PacketCodec<RegistryByteBuf, ExitViewPayload> CODEC = PacketCodec.unit(new ExitViewPayload());



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