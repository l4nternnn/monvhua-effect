package com.kuilunfuzhe.monvhua.network.gazeguidance;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * 服务端 -> 客户端：标记粒子特效。
 * 服务端指定世界坐标位置，通知客户端在该位置生成标记相关粒子效果。
 */
public record MarkParticleS2CPacket(Vec3d pos) implements CustomPayload {
    public static final Id<MarkParticleS2CPacket> ID = new Id<>(Identifier.of("monvhua", "mark_particle"));
    public static final PacketCodec<RegistryByteBuf, MarkParticleS2CPacket> CODEC = PacketCodec.of(MarkParticleS2CPacket::write, MarkParticleS2CPacket::new);

    /**
     * 从网络缓冲区读取坐标数据构造数据包。
     */
    private MarkParticleS2CPacket(RegistryByteBuf buf) {
        this(new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble()));
    }

    /**
     * 将坐标数据写入网络缓冲区。
     */
    private void write(RegistryByteBuf buf) {
        buf.writeDouble(pos.x);
        buf.writeDouble(pos.y);
        buf.writeDouble(pos.z);
    }


    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

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
}