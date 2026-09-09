package com.kuilunfuzhe.monvhua.features.glitch.client;

import com.kuilunfuzhe.monvhua.features.glitch.GlitchPlane;
import com.kuilunfuzhe.monvhua.network.glitch.GlitchPlanePackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import java.util.List;

public final class GlitchPlaneClientFeature {
    private static volatile List<GlitchPlane> planes = List.of();
    private static boolean initialized;
    private GlitchPlaneClientFeature() {}
    public static void initializeClient() {
        if (initialized) return;
        initialized = true;
        ClientPlayNetworking.registerGlobalReceiver(GlitchPlanePackets.FullSyncS2C.ID,
                (packet, context) -> context.client().execute(() -> planes = packet.planes()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> planes = List.of());
    }
    public static void render(WorldRenderContext context) { GlitchPlaneRenderer.render(context, planes); }
}
