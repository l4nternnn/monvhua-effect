package com.kuilunfuzhe.monvhua.network.openback;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 客户端 -> 服务端：携带实体请求。
 * 客户端发送目标实体 ID，请求将该实体背起/携带。
 */
public record CarryEntityPayload(int entityId) implements CustomPayload {
    public static final CustomPayload.Id<CarryEntityPayload> ID =
            new CustomPayload.Id<>(Identifier.of("monvhua", "carry_entity"));
    public static final PacketCodec<RegistryByteBuf, CarryEntityPayload> CODEC =
            PacketCodec.tuple(PacketCodecs.INTEGER, CarryEntityPayload::entityId, CarryEntityPayload::new);


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