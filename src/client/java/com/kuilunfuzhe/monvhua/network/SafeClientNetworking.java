package com.kuilunfuzhe.monvhua.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.packet.CustomPayload;

public final class SafeClientNetworking {
    public static boolean send(CustomPayload payload) {
        if (payload == null || payload.getId() == null) return false;
        try {
            if (!ClientPlayNetworking.canSend(payload.getId())) return false;
            ClientPlayNetworking.send(payload);
            return true;
        } catch (IllegalArgumentException | IllegalStateException ignored) {
            return false;
        }
    }

    private SafeClientNetworking() {
    }
}
