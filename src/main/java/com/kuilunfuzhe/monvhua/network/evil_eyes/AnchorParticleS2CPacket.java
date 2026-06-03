// 锚点粒子 S2C 数据包
package com.kuilunfuzhe.monvhua.network.evil_eyes;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

/**
 * 服务端→客户端：在锚点位置生成粒子特效。
 * 服务端在锚点发生交互事件时发送，客户端收到后在目标位置播放对应类型粒子动画。
 */
public record AnchorParticleS2CPacket(UUID standId, Vec3d pos, int type) implements CustomPayload {
    public static final Id<AnchorParticleS2CPacket> ID = new Id<>(Identifier.of("monvhua", "anchor_particle"));
    public static final PacketCodec<RegistryByteBuf, AnchorParticleS2CPacket> CODEC = PacketCodec.of(
            (packet, buf) -> {
                buf.writeUuid(packet.standId);
                buf.writeDouble(packet.pos.x);
                buf.writeDouble(packet.pos.y);
                buf.writeDouble(packet.pos.z);
                buf.writeInt(packet.type);
            },
            buf -> new AnchorParticleS2CPacket(buf.readUuid(), new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readInt())
    );
    private static boolean registered = false;
    /** 注册数据包类型到 S2C 载荷注册表（幂等） */
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