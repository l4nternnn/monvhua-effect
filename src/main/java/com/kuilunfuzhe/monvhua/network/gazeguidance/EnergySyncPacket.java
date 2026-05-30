package com.kuilunfuzhe.monvhua.network.gazeguidance;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 服务端 -> 客户端：凝视引导能量同步。
 * 服务端定期同步当前能量值与最大能量值，用于客户端渲染能量条显示。
 */
public record EnergySyncPacket(double currentEnergy, double maxEnergy) implements CustomPayload {
    public static final Id<EnergySyncPacket> ID = new Id<>(Identifier.of("monvhua", "energy_sync"));
    public static final PacketCodec<RegistryByteBuf, EnergySyncPacket> CODEC = PacketCodec.of(EnergySyncPacket::write, EnergySyncPacket::new);

    /**
     * 从网络缓冲区读取能量数据构造数据包。
     */
    private EnergySyncPacket(RegistryByteBuf buf) {
        this(buf.readDouble(), buf.readDouble());
    }

    /**
     * 将能量数据写入网络缓冲区。
     */
    private void write(RegistryByteBuf buf) {
        buf.writeDouble(currentEnergy);
        buf.writeDouble(maxEnergy);
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