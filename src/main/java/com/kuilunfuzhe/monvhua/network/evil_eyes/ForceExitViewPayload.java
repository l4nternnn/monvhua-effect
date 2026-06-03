package com.kuilunfuzhe.monvhua.network.evil_eyes;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 服务端→客户端：强制退出当前观察视角。
 * 服务端在触发条件（如被观察实体死亡、距离过远）时发送，客户端收到后强制恢复第三人称视角。
 */
public record ForceExitViewPayload() implements CustomPayload {
    public static final Id<ForceExitViewPayload> ID = new Id<>(Identifier.of("monvhua", "force_exit_view"));
    public static final PacketCodec<RegistryByteBuf, ForceExitViewPayload> CODEC = PacketCodec.unit(new ForceExitViewPayload());


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