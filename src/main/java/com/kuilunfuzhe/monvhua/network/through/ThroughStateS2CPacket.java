package com.kuilunfuzhe.monvhua.network.through;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 服务端 -> 客户端：隐秘/穿墙状态同步。
 * 服务端通知客户端当前是否隐身、是否应启用客户端 noClip、是否锁定穿墙输入及锁定视角。
 */
public record ThroughStateS2CPacket(
        /** 是否处于隐身状态 */
        boolean invisible,
        /** 客户端是否应开启 noClip（穿墙尝试或穿墙锁定时为 true） */
        boolean phaseNoClip,
        /** 是否处于进入墙体后的穿墙锁定状态 */
        boolean phaseLocked,
        /** 是否处于松开使用键/被沉默后的墙内滞留状态 */
        boolean phaseStalled,
        /** 穿墙锁定时固定的水平视角 */
        float lockedYaw,
        /** 穿墙锁定时固定的俯仰视角 */
        float lockedPitch,
        /** 渐隐动画剩余刻数（tick），用于客户端控制透明度过渡 */
        int fadeOutTicks
) implements CustomPayload {
    public static final Id<ThroughStateS2CPacket> ID = new Id<>(Identifier.of("monvhua", "secrecy_state"));
    public static final PacketCodec<RegistryByteBuf, ThroughStateS2CPacket> CODEC = PacketCodec.of(ThroughStateS2CPacket::write, ThroughStateS2CPacket::new);

    /**
     * 从网络缓冲区读取状态数据构造数据包。
     */
    private ThroughStateS2CPacket(RegistryByteBuf buf) {
        this(buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readFloat(), buf.readFloat(), buf.readVarInt());
    }

    /**
     * 将状态数据写入网络缓冲区。
     */
    private void write(RegistryByteBuf buf) {
        buf.writeBoolean(invisible);
        buf.writeBoolean(phaseNoClip);
        buf.writeBoolean(phaseLocked);
        buf.writeBoolean(phaseStalled);
        buf.writeFloat(lockedYaw);
        buf.writeFloat(lockedPitch);
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
