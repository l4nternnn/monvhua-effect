package com.kuilunfuzhe.monvhua.network.evil_eyes;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * 服务端→客户端：在指定坐标生成爆炸粒子特效。
 * 服务端在锚点被销毁时发送，客户端收到后在目标位置播放爆炸粒子动画。
 */
public record ExplosionParticleS2CPacket(Vec3d pos) implements CustomPayload {
    public static final Id<ExplosionParticleS2CPacket> ID = new Id<>(Identifier.of("monvhua", "explosion_particle"));
    public static final PacketCodec<RegistryByteBuf, ExplosionParticleS2CPacket> CODEC = PacketCodec.of(
            (packet, buf) -> {
                buf.writeDouble(packet.pos.x);
                buf.writeDouble(packet.pos.y);
                buf.writeDouble(packet.pos.z);
            },
            buf -> new ExplosionParticleS2CPacket(new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble()))
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