package com.kuilunfuzhe.monvhua.network.mirror;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 客户端 -> 服务端：镜子蓄力请求。
 * 客户端发送蓄力开始或停止信号，用于控制镜子技能的蓄力状态。
 */
public record MirrorChargeC2SPacket(boolean start) implements CustomPayload {
    public static final Id<MirrorChargeC2SPacket> ID = new Id<>(Identifier.of("monvhua", "mirror_charge"));
    public static final PacketCodec<RegistryByteBuf, MirrorChargeC2SPacket> CODEC = PacketCodec.tuple(
            PacketCodecs.BOOLEAN, MirrorChargeC2SPacket::start,
            MirrorChargeC2SPacket::new
    );
    private static boolean registered = false;

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

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
