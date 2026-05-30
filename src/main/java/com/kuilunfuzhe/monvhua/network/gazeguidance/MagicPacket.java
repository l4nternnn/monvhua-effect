package com.kuilunfuzhe.monvhua.network.gazeguidance;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 客户端 -> 服务端：魔法触发请求。
 * 客户端发送目标实体的 entityId，请求对该实体施放魔法/技能。
 */
public record MagicPacket(int entityId) implements CustomPayload {
    public static final Id<MagicPacket> ID = new Id<>(Identifier.of("monvhua", "magic"));
    public static final PacketCodec<RegistryByteBuf, MagicPacket> CODEC = PacketCodec.tuple(
            PacketCodecs.INTEGER, MagicPacket::entityId,
            MagicPacket::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {

        return ID;
    }

    private static boolean registered = false;

    /**
     * 注册此数据包到 C2S 负载类型注册表。
     */
    public static void register() {
        if (!registered) {

            PayloadTypeRegistry.playC2S().register(ID, CODEC);
            registered = true;
        }
    }
}