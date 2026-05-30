package com.kuilunfuzhe.monvhua.network.evil_eyes;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 客户端→服务端：请求标记指定实体。
 * 客户端在玩家对目标实体执行标记操作时发送，服务端处理标记逻辑。
 */
public record MarkEntityPayload(int entityId) implements CustomPayload {
    public static final Id<MarkEntityPayload> ID = new Id<>(Identifier.of("monvhua", "mark_entity"));
    public static final PacketCodec<RegistryByteBuf, MarkEntityPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.INTEGER, MarkEntityPayload::entityId,
            MarkEntityPayload::new
    );


    private static boolean registered = false;
    /** 注册数据包类型到 C2S 载荷注册表（幂等） */
    public static void register() {
        if (!registered) {
            net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S().register(ID, CODEC);
            registered = true;
        }
    }
    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}