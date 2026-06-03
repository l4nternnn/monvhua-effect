package com.kuilunfuzhe.monvhua.network.evil_eyes;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** 服务端→客户端：下发邪恶之眼全局配置信息。客户端收到后使用 JSON 更新本地配置。 */
public record GlobalConfigS2CPacket(String json) implements CustomPayload {
    public static final Id<GlobalConfigS2CPacket> ID = new Id<>(Identifier.of("monvhua", "global_config"));
    public static final PacketCodec<PacketByteBuf, GlobalConfigS2CPacket> CODEC =
            PacketCodec.tuple(PacketCodecs.STRING, GlobalConfigS2CPacket::json, GlobalConfigS2CPacket::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }

    private static boolean registered = false;
    /** 注册数据包类型到 S2C 载荷注册表（幂等） */
    public static void register() {
        if (!registered) {
            net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C().register(ID, CODEC);
            registered = true;
        }
    }

    /** 各阶段配置参数：每日限制、最大标记数、分数区间、观察时长、鹦鹉相关限制等。 */
    public record StageConfig(int dailyLimit, int maxMarks, int minScore, int maxScore, int watchRequiredTicks, int parrotDailyLimit, int maxActiveParrots) {}
}
