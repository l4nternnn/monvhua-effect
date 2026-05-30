package com.kuilunfuzhe.monvhua.network.mirror;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 服务端 -> 客户端：镜子蓄力进度同步。
 * 服务端定期向客户端发送当前蓄力刻数和最大蓄力刻数，用于客户端渲染蓄力条。
 */
public record MirrorChargeSyncS2CPacket(int currentTicks, int maxTicks) implements CustomPayload {
    public static final Id<MirrorChargeSyncS2CPacket> ID = new Id<>(Identifier.of("monvhua", "mirror_charge_sync"));
    public static final PacketCodec<RegistryByteBuf, MirrorChargeSyncS2CPacket> CODEC = PacketCodec.of(
            MirrorChargeSyncS2CPacket::write, MirrorChargeSyncS2CPacket::new
    );

    /**
     * 从网络缓冲区读取数据构造数据包。
     */
    private MirrorChargeSyncS2CPacket(RegistryByteBuf buf) {
        this(buf.readInt(), buf.readInt());
    }

    /**
     * 将数据包写入网络缓冲区。
     */
    private void write(RegistryByteBuf buf) {
        buf.writeInt(currentTicks);
        buf.writeInt(maxTicks);
    }

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
            PayloadTypeRegistry.playS2C().register(ID, CODEC);
            registered = true;
        }
    }
}
