package com.kuilunfuzhe.monvhua.network.evil_eyes;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * 服务端→客户端：通知某实体已被标记。
 * 服务端在处理标记逻辑后发送，客户端收到后更新标记状态并显示实体名称。
 */
public record EntityMarkedPayload(UUID entityUuid, String entityName) implements CustomPayload {
    public static final Id<EntityMarkedPayload> ID = new Id<>(Identifier.of("monvhua", "entity_marked"));
    public static final PacketCodec<RegistryByteBuf, EntityMarkedPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING.xmap(UUID::fromString, UUID::toString), EntityMarkedPayload::entityUuid,
            PacketCodecs.STRING, EntityMarkedPayload::entityName,
            EntityMarkedPayload::new
    );


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