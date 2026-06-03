package com.kuilunfuzhe.monvhua.network.mirror;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 客户端 -> 服务端：镜子配置更新。
 * 客户端将修改后的镜子配置以 JSON 字符串形式发送给服务端保存。
 */
public record MirrorConfigUpdateC2SPacket(String json) implements CustomPayload {
    public static final Id<MirrorConfigUpdateC2SPacket> ID = new Id<>(Identifier.of("monvhua", "mirror_config_update"));
    public static final PacketCodec<RegistryByteBuf, MirrorConfigUpdateC2SPacket> CODEC = PacketCodec.of(MirrorConfigUpdateC2SPacket::write, MirrorConfigUpdateC2SPacket::new);

    /**
     * 从网络缓冲区读取 JSON 字符串构造数据包。
     */
    private MirrorConfigUpdateC2SPacket(RegistryByteBuf buf) {
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
     * 注册此数据包到 C2S 负载类型注册表。
     */
    public static void register() {
        if (!registered) {
            PayloadTypeRegistry.playC2S().register(ID, CODEC);
            registered = true;
        }
    }
}
