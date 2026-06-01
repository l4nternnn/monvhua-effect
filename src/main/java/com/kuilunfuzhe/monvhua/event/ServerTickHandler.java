package com.kuilunfuzhe.monvhua.event;

import com.kuilunfuzhe.monvhua.features.floating.floating;
import com.kuilunfuzhe.monvhua.network.floating.FloatingEnergySyncS2CPacket;
import com.kuilunfuzhe.monvhua.network.floating.FullWitchTagSyncS2CPacket;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

public class ServerTickHandler {

    private static int syncTick = 0;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // 能量消耗和回复
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                floating.tickEnergy(player);
            }

            // 每 5 tick 同步一次能量到客户端
            syncTick++;
            if (syncTick >= 5) {
                syncTick = 0;
                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    double current = floating.getEnergy(player);
                    double max = 100;
                    ServerPlayNetworking.send(player, new FloatingEnergySyncS2CPacket(current, max));
                    ServerPlayNetworking.send(player, new FullWitchTagSyncS2CPacket(
                            player.getCommandTags().contains("MonvhuaFull")
                    ));
                }
            }
        });
    }
}