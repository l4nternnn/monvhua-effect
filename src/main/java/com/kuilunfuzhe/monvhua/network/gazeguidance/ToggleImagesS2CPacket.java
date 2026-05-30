package com.kuilunfuzhe.monvhua.network.gazeguidance;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 服务端 -> 客户端：图像显示开关。
 * 服务端通知客户端是否启用/禁用图像渲染功能（如标记图标等）。
 */
public record ToggleImagesS2CPacket(boolean enabled) implements CustomPayload {
    public static final Id<ToggleImagesS2CPacket> ID = new Id<>(Identifier.of("monvhua", "toggle_images"));
    public static final PacketCodec<RegistryByteBuf, ToggleImagesS2CPacket> CODEC = PacketCodec.of(
            (packet, buf) -> buf.writeBoolean(packet.enabled),
            buf -> new ToggleImagesS2CPacket(buf.readBoolean())
    );

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

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}