package com.kuilunfuzhe.monvhua.network.gazeguidance;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 服务端 -> 客户端：凝视引导焦点状态。
 * 通知客户端当前是否处于凝视聚焦状态，用于切换瞄准/标记模式的 UI 显示。
 */
public record FocusStatusPacket(boolean active) implements CustomPayload {
    public static final Id<FocusStatusPacket> ID = new Id<>(Identifier.of("monvhua", "focus_status"));
    public static final PacketCodec<RegistryByteBuf, FocusStatusPacket> CODEC = PacketCodec.tuple(
            PacketCodecs.BOOLEAN, FocusStatusPacket::active,
            FocusStatusPacket::new
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
            net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C().register(ID, CODEC);
            registered = true;
        }
    }
}