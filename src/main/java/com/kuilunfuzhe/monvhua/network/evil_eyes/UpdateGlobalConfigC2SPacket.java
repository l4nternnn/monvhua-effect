package com.kuilunfuzhe.monvhua.network.evil_eyes;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** 客户端→服务端：更新指定阶段的全局配置参数。管理员通过配置界面发送，服务端持久化并广播给所有客户端。 */
public record UpdateGlobalConfigC2SPacket(int stage, int dailyLimit, int maxMarks, int minScore, int maxScore, int watchRequiredTicks,int parrotDailyLimit, int maxActiveParrots) implements CustomPayload {
    public static final Id<UpdateGlobalConfigC2SPacket> ID = new Id<>(Identifier.of("monvhua", "update_global_config"));
    public static final PacketCodec<RegistryByteBuf, UpdateGlobalConfigC2SPacket> CODEC = PacketCodec.tuple(
            PacketCodecs.INTEGER, UpdateGlobalConfigC2SPacket::stage,
            PacketCodecs.INTEGER, UpdateGlobalConfigC2SPacket::dailyLimit,
            PacketCodecs.INTEGER, UpdateGlobalConfigC2SPacket::maxMarks,
            PacketCodecs.INTEGER, UpdateGlobalConfigC2SPacket::minScore,
            PacketCodecs.INTEGER, UpdateGlobalConfigC2SPacket::maxScore,
            PacketCodecs.INTEGER, UpdateGlobalConfigC2SPacket::watchRequiredTicks,
            PacketCodecs.INTEGER, UpdateGlobalConfigC2SPacket::parrotDailyLimit,
            PacketCodecs.INTEGER, UpdateGlobalConfigC2SPacket::maxActiveParrots,
            UpdateGlobalConfigC2SPacket::new
    );

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