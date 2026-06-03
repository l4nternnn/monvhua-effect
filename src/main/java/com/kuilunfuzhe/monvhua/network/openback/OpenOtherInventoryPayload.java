package com.kuilunfuzhe.monvhua.network.openback;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 客户端 -> 服务端：打开其他实体背包。
 * 客户端发送目标实体 ID，请求打开该实体的背包界面（如玩家、可驮运实体等）。
 */
public record OpenOtherInventoryPayload(int targetEntityId) implements CustomPayload {
    public static final CustomPayload.Id<OpenOtherInventoryPayload> ID =
            new CustomPayload.Id<>(Identifier.of("monvhua", "open_other_inv"));
    public static final PacketCodec<RegistryByteBuf, OpenOtherInventoryPayload> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.INTEGER, OpenOtherInventoryPayload::targetEntityId,
                    OpenOtherInventoryPayload::new
            );

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