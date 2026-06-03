package com.kuilunfuzhe.monvhua.network.evil_eyes;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * 客户端→服务端：在指定位置放置鹦鹉实体。
 * 客户端在玩家使用鹦鹉放置功能时发送，服务端收到后在目标位置生成鹦鹉。
 */
public record PlaceParrotC2SPacket(Vec3d pos) implements CustomPayload {
    public static final Id<PlaceParrotC2SPacket> ID = new Id<>(Identifier.of("monvhua", "place_parrot"));
    public static final PacketCodec<RegistryByteBuf, PlaceParrotC2SPacket> CODEC = PacketCodec.of(
            (packet, buf) -> {
                buf.writeDouble(packet.pos.x);
                buf.writeDouble(packet.pos.y);
                buf.writeDouble(packet.pos.z);
            },
            buf -> new PlaceParrotC2SPacket(new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble()))
    );


    private static boolean registered = false;

    /** 注册数据包类型到 C2S 载荷注册表（幂等） */
    public static void register() {
        if (!registered) {

            PayloadTypeRegistry.playC2S().register(ID, CODEC);
            registered = true;
        }
    }
    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}