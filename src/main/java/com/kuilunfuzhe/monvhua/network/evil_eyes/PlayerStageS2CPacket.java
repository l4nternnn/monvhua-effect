// 文件：com.kuilunfuzhe.monvhua.network.clairvoyance.PlayerStageS2CPacket
package com.kuilunfuzhe.monvhua.network.evil_eyes;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** 服务端→客户端：同步玩家当前的邪恶之眼阶段等级。客户端收到后更新本地 UI 显示。 */
public record PlayerStageS2CPacket(int stage) implements CustomPayload {
    public static final Id<PlayerStageS2CPacket> ID = new Id<>(Identifier.of("monvhua", "player_stage"));
    public static final PacketCodec<PacketByteBuf, PlayerStageS2CPacket> CODEC =
            PacketCodec.tuple(PacketCodecs.INTEGER, PlayerStageS2CPacket::stage, PlayerStageS2CPacket::new);

    private static boolean registered = false;
    /** 注册数据包类型到 S2C 载荷注册表（幂等） */
    public static void register() {
        if (!registered) {
            net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C().register(ID, CODEC);
            registered = true;
        }
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}