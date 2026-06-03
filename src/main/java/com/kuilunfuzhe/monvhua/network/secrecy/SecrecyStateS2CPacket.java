package com.kuilunfuzhe.monvhua.network.secrecy;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 服务端 -> 客户端：隐身状态同步。
 * 服务端通知客户端当前隐身状态，包括是否隐身及渐隐动画剩余刻数。
 */
public record SecrecyStateS2CPacket(
	/** 是否处于隐身状态 */
	boolean invisible,
	/** 渐隐动画剩余刻数（tick），用于客户端控制透明度过渡 */
	int fadeOutTicks
) implements CustomPayload {
    public static final Id<SecrecyStateS2CPacket> ID = new Id<>(Identifier.of("monvhua", "secrecy_state"));
    public static final PacketCodec<RegistryByteBuf, SecrecyStateS2CPacket> CODEC = PacketCodec.of(SecrecyStateS2CPacket::write, SecrecyStateS2CPacket::new);

    /**
     * 从网络缓冲区读取状态数据构造数据包。
     */
    private SecrecyStateS2CPacket(RegistryByteBuf buf) {
        this(buf.readBoolean(), buf.readVarInt());
    }

    /**
     * 将状态数据写入网络缓冲区。
     */
    private void write(RegistryByteBuf buf) {
        buf.writeBoolean(invisible);
        buf.writeVarInt(fadeOutTicks);
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
