package com.kuilunfuzhe.monvhua.network.openback;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 客户端 -> 服务端：放下携带实体请求。
 * 客户端发送空包，请求将当前背起的实体放下到世界中。
 */
public record PlaceCarriedEntityPayload() implements CustomPayload {
    public static final CustomPayload.Id<PlaceCarriedEntityPayload> ID =
            new CustomPayload.Id<>(Identifier.of("monvhua", "place_carried_entity"));
    public static final PacketCodec<RegistryByteBuf, PlaceCarriedEntityPayload> CODEC =
            PacketCodec.unit(new PlaceCarriedEntityPayload());


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