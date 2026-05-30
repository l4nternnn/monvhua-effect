package com.kuilunfuzhe.monvhua.network.mirror;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 服务端 -> 客户端：镜子配置数据同步。
 * 服务端将当前玩家的镜子配置以 JSON 字符串形式发送给客户端。
 */
public record MirrorConfigS2CPacket(String json) implements CustomPayload {
    public static final Id<MirrorConfigS2CPacket> ID = new Id<>(Identifier.of("monvhua", "mirror_config_sync"));
    public static final PacketCodec<RegistryByteBuf, MirrorConfigS2CPacket> CODEC = PacketCodec.of(MirrorConfigS2CPacket::write, MirrorConfigS2CPacket::new);

    /**
     * 从网络缓冲区读取 JSON 字符串构造数据包。
     */
    private MirrorConfigS2CPacket(RegistryByteBuf buf) {
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
