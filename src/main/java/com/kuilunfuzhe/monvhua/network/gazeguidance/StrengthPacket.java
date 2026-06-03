package com.kuilunfuzhe.monvhua.network.gazeguidance;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 服务端 -> 客户端：凝视引导强度参数同步。
 * 服务端通知客户端当前凝视引导的等级及相关属性参数，用于客户端计算和显示技能强度。
 */
public record StrengthPacket(
	/** 当前强度等级 */
	int stage,
	/** 能量消耗速率 */
	double drain,
	/** 能量恢复速率 */
	double regen,
	/** 技能有效范围 */
	double range,
	/** 最大可标记数量 */
	int maxMarks
) implements CustomPayload {
    public static final Id<StrengthPacket> ID = new Id<>(Identifier.of("monvhua", "strength"));
    public static final PacketCodec<RegistryByteBuf, StrengthPacket> CODEC = PacketCodec.of(StrengthPacket::write, StrengthPacket::new);

    /**
     * 从网络缓冲区读取强度参数构造数据包。
     */
    private StrengthPacket(RegistryByteBuf buf) {
        this(buf.readInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readInt());
    }

    /**
     * 将强度参数写入网络缓冲区。
     */
    private void write(RegistryByteBuf buf) {
        buf.writeInt(stage);
        buf.writeDouble(drain);
        buf.writeDouble(regen);
        buf.writeDouble(range);
        buf.writeInt(maxMarks);
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