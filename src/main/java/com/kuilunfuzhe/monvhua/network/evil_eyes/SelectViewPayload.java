package com.kuilunfuzhe.monvhua.network.evil_eyes;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * 双向（C2S / S2C）：选择或确认观察指定实体。
 * 客户端发送以请求观察目标，服务端发送以确认允许观察；同时注册了两端以支持双向通信。
 */
public record SelectViewPayload(UUID entityUuid) implements CustomPayload {
    public static final Id<SelectViewPayload> ID = new Id<>(Identifier.of("monvhua", "select_view"));
    public static final PacketCodec<PacketByteBuf, SelectViewPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING.xmap(UUID::fromString, UUID::toString), SelectViewPayload::entityUuid,
            SelectViewPayload::new
    );

    private static boolean registered = false;
    /** 注册数据包类型到 C2S 和 S2C 载荷注册表（幂等，双向通信） */
    public static void register() {
        if (!registered) {
            registered = true;
            PayloadTypeRegistry.playC2S().register(ID, CODEC);
            PayloadTypeRegistry.playS2C().register(ID, CODEC);
        }
    }
    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}