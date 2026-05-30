package com.kuilunfuzhe.monvhua.network.gazeguidance;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 服务端 -> 客户端：凝视引导配置同步。
 * 服务端将当前玩家的凝视引导配置以 JSON 字符串形式发送给客户端。
 */
public record SyncConfigS2CPacket(String json) implements CustomPayload {
    public static final Id<SyncConfigS2CPacket> ID = new Id<>(Identifier.of("monvhua", "sync_config"));
    public static final PacketCodec<RegistryByteBuf, SyncConfigS2CPacket> CODEC = PacketCodec.of(SyncConfigS2CPacket::write, SyncConfigS2CPacket::new);

    /**
     * 从网络缓冲区读取 JSON 字符串构造数据包。
     */
    private SyncConfigS2CPacket(RegistryByteBuf buf) {
        this(buf.readString());
    }

    /**
     * 将 JSON 字符串写入网络缓冲区。
     */
    private void write(RegistryByteBuf buf) {
        buf.writeString(json);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    /**
     * 注册此数据包到 S2C 负载类型注册表。
     */
    private static boolean registered = false;
    public static void register() {
        if (!registered) {
            net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C().register(ID, CODEC);
            registered = true;
        }
    }
}