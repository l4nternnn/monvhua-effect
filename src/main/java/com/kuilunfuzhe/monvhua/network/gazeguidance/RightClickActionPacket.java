package com.kuilunfuzhe.monvhua.network.gazeguidance;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 客户端 -> 服务端：右键动作通知。
 * 客户端发送右键按下/松开信号，用于触发或取消凝视引导的相关技能。
 */
public record RightClickActionPacket(boolean start) implements CustomPayload {
    public static final Id<RightClickActionPacket> ID = new Id<>(Identifier.of("monvhua", "rightclick"));
    public static final PacketCodec<RegistryByteBuf, RightClickActionPacket> CODEC = PacketCodec.tuple(
            PacketCodecs.BOOLEAN, RightClickActionPacket::start,
            RightClickActionPacket::new
    );
    /**
     * 注册此数据包到 C2S 负载类型注册表。
     */
    private static boolean registered = false;

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public static void register() {
        if (!registered) {
            net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S().register(ID, CODEC);
            registered = true;
        }
    }
}