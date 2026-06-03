package com.kuilunfuzhe.monvhua.network.mirror;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 客户端 -> 服务端：镜子开关切换请求。
 * 客户端发送无负载的空包，请求切换当前镜子的开启/关闭状态。
 */
public record MirrorToggleC2SPacket() implements CustomPayload {
	public static final Id<MirrorToggleC2SPacket> ID = new Id<>(Identifier.of("monvhua", "mirror_toggle"));
	public static final PacketCodec<PacketByteBuf, MirrorToggleC2SPacket> CODEC = PacketCodec.unit(new MirrorToggleC2SPacket());

	private static boolean registered = false;

	/**
	 * 注册此数据包到 C2S 负载类型注册表。
	 */
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
