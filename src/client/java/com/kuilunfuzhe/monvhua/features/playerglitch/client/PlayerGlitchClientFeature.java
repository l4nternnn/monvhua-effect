package com.kuilunfuzhe.monvhua.features.playerglitch.client;

import com.kuilunfuzhe.monvhua.network.playerglitch.PlayerGlitchPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.util.math.MathHelper;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerGlitchClientFeature {
    private static final Map<UUID, PlayerGlitchPackets.StateS2C> STATES = new HashMap<>();
    private static boolean initialized;
    private PlayerGlitchClientFeature() {}
    public static void initializeClient() {
        if (initialized) return;
        initialized = true;
        ClientPlayNetworking.registerGlobalReceiver(PlayerGlitchPackets.StateS2C.ID, (packet, context) ->
                context.client().execute(() -> {
                    PlayerGlitchPackets.StateS2C previous = STATES.get(packet.target());
                    if (previous != null && packet.revision() < previous.revision()) {
                        return;
                    }
                    if (packet.enabled()) STATES.put(packet.target(), packet);
                    else STATES.remove(packet.target());
                }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> STATES.clear());
    }
    public static void render(WorldRenderContext context) {
        if (STATES.isEmpty() || context.world() == null) return;
        PlayerGlitchRenderer.render(context, STATES);
    }
    static int clampFragments(int value) { return MathHelper.clamp(value, 1, 256); }
}
