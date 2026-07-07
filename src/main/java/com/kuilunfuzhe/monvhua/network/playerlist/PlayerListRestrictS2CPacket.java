package com.kuilunfuzhe.monvhua.network.playerlist;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 服务端 -> 客户端：同步玩家列表限制状态。
 * 当 restricted 为 true 时，客户端处于生存/冒险模式的玩家将无法通过Tab键打开玩家列表。
 */
public record PlayerListRestrictS2CPacket(boolean restricted) implements CustomPayload {
    public static final Id<PlayerListRestrictS2CPacket> ID = new Id<>(Identifier.of("monvhua", "playerlist_restrict"));
    public static final PacketCodec<RegistryByteBuf, PlayerListRestrictS2CPacket> CODEC = PacketCodec.of(
            (packet, buf) -> buf.writeBoolean(packet.restricted),
            buf -> new PlayerListRestrictS2CPacket(buf.readBoolean())
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    private static boolean registered = false;

    /**
     * 注册此数据包到 S2C 负载类型注册表。
     */
    public static void register() {
        if (!registered) {
            PayloadTypeRegistry.playS2C().register(ID, CODEC);
            registered = true;
        }
    }
}
