package com.kuilunfuzhe.monvhua.features.daynight;

import icyllis.modernui.core.Core;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;

public final class DayNightClient {
    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(DayNightPackets.State.ID, (packet, context) ->
                context.client().execute(() -> {
                    var connection = context.client().getNetworkHandler();
                    DayNightClientState.update(packet);
                    if (Core.getUiHandlerAsync() != null) Core.postOnUiThread(() -> {
                        if (MinecraftClient.getInstance().getNetworkHandler() == connection)
                            DayNightConfigScreen.receive(packet);
                    });
                }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            DayNightClientState.clear();
            if (Core.getUiHandlerAsync() != null) Core.postOnUiThread(DayNightConfigScreen::disconnect);
        });
    }
    static void send(DayNightPackets.Request request) {
        MinecraftClient.getInstance().execute(() -> {
            if (MinecraftClient.getInstance().getNetworkHandler() != null && ClientPlayNetworking.canSend(DayNightPackets.Request.ID))
                ClientPlayNetworking.send(request);
            else Core.postOnUiThread(DayNightConfigScreen::disconnect);
        });
    }
}
