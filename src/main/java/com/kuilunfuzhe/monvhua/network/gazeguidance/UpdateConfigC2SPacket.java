package com.kuilunfuzhe.monvhua.network.gazeguidance;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 客户端 -> 服务端：凝视引导配置更新。
 * 客户端将修改后的凝视引导配置以 JSON 字符串形式发送给服务端保存。
 */
public record UpdateConfigC2SPacket(String json) implements CustomPayload {
    public static final Id<UpdateConfigC2SPacket> ID = new Id<>(Identifier.of("monvhua", "update_config"));
    public static final PacketCodec<RegistryByteBuf, UpdateConfigC2SPacket> CODEC = PacketCodec.of(UpdateConfigC2SPacket::write, UpdateConfigC2SPacket::new);

    /**
     * 从网络缓冲区读取 JSON 字符串构造数据包。
     */
    private UpdateConfigC2SPacket(RegistryByteBuf buf) {
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