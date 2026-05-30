package com.kuilunfuzhe.monvhua.network.evil_eyes;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * 客户端→服务端：销毁指定的锚点。
 * 客户端在主动移除锚点时发送，服务端收到后清除对应锚点数据。
 */
public record AnchorDestroyC2SPacket(UUID standId) implements CustomPayload {
    public static final Id<AnchorDestroyC2SPacket> ID = new Id<>(Identifier.of("monvhua", "anchor_destroy"));
    public static final PacketCodec<RegistryByteBuf, AnchorDestroyC2SPacket> CODEC = PacketCodec.of(
            (packet, buf) -> buf.writeUuid(packet.standId),
            buf -> new AnchorDestroyC2SPacket(buf.readUuid())
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