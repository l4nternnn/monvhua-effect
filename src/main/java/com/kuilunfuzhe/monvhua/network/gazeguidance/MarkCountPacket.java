package com.kuilunfuzhe.monvhua.network.gazeguidance;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 服务端 -> 客户端：标记数量同步。
 * 服务端通知客户端当前已被标记的实体数量，用于客户端 UI 显示。
 */
public record MarkCountPacket(int count) implements CustomPayload {
    public static final Id<MarkCountPacket> ID = new Id<>(Identifier.of("monvhua", "mark_count"));
    public static final PacketCodec<RegistryByteBuf, MarkCountPacket> CODEC = PacketCodec.tuple(
            PacketCodecs.INTEGER, MarkCountPacket::count,
            MarkCountPacket::new
    );
    /**
     * 注册此数据包到 S2C 负载类型注册表。
     */
    private static boolean registered = false;

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public static void register() {
        if (!registered) {
            net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C().register(ID, CODEC);
            registered = true;
        }
    }
}