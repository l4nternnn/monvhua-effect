package com.kuilunfuzhe.monvhua.network.gazeguidance;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * 服务端 -> 客户端：粒子特效播放。
 * 服务端指定世界坐标，通知客户端在该位置播放通用粒子视觉效果。
 */
public record ParticlePacket(double x, double y, double z) implements CustomPayload {
    public static final Id<ParticlePacket> ID = new Id<>(Identifier.of("monvhua", "particle"));
    public static final PacketCodec<RegistryByteBuf, ParticlePacket> CODEC = PacketCodec.tuple(
            PacketCodecs.DOUBLE, ParticlePacket::x,
            PacketCodecs.DOUBLE, ParticlePacket::y,
            PacketCodecs.DOUBLE, ParticlePacket::z,
            ParticlePacket::new
    );
    private static boolean registered = false;

    /**
     * 从 Vec3d 位置构造数据包。
     */
    public ParticlePacket(Vec3d pos) {
        this(pos.x, pos.y, pos.z);
    }

    /**
     * 获取位置向量。
     */
    public Vec3d getPos() {
        return new Vec3d(x, y, z);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

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