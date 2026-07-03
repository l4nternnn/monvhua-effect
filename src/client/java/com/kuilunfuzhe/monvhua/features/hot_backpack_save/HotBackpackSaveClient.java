package com.kuilunfuzhe.monvhua.features.hot_backpack_save;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.kuilunfuzhe.monvhua.network.hot_backpack_save.HotBackpackPackets;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

public final class HotBackpackSaveClient {
    static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static HotBackpackState state = new HotBackpackState();
    private static Screen pendingParent;

    private HotBackpackSaveClient() {
    }

    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(HotBackpackPackets.StateS2C.ID, (packet, context) ->
                context.client().execute(() -> {
                    try {
                        state = GSON.fromJson(packet.json(), HotBackpackState.class);
                        if (state == null) {
                            state = new HotBackpackState();
                        }
                        state.sanitize();
                        HotBackpackSaveFragment.receiveState(state);
                    } catch (Exception ignored) {
                        state = new HotBackpackState();
                    }
                }));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (pendingParent != null && client.currentScreen == null) {
                Screen parent = pendingParent;
                pendingParent = null;
                HotBackpackSaveFragment.open(null);
            }
        });
    }

    public static void openAfterWorldFrame(Screen parent) {
        requestState();
        pendingParent = parent;
        MinecraftClient.getInstance().execute(() -> MinecraftClient.getInstance().setScreen(null));
    }

    static HotBackpackState state() {
        state.sanitize();
        return state;
    }

    static void requestState() {
        if (MinecraftClient.getInstance().player != null) {
            ClientPlayNetworking.send(new HotBackpackPackets.RequestStateC2S());
        }
    }
}
