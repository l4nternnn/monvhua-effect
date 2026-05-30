package com.kuilunfuzhe.monvhua.network.evil_eyes;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/** 客户端→服务端：请求取消标记指定实体。客户端在玩家取消标记操作时发送。 */
public record UnmarkEntityPayload(UUID entityUuid) implements CustomPayload {
    public static final Id<UnmarkEntityPayload> ID = new Id<>(Identifier.of("monvhua", "unmark_entity"));
    public static final PacketCodec<RegistryByteBuf, UnmarkEntityPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING.xmap(UUID::fromString, UUID::toString), UnmarkEntityPayload::entityUuid,
            UnmarkEntityPayload::new
    );

    private static boolean registered = false;
    /** 注册数据包类型到 C2S 载荷注册表（幂等） */
    public static void register() {
        if (!registered) {
            registered = true;
            PayloadTypeRegistry.playC2S().register(ID, CODEC);
        }
    }
    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}