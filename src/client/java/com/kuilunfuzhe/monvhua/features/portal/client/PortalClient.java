package com.kuilunfuzhe.monvhua.features.portal.client;

import com.kuilunfuzhe.monvhua.features.portal.PortalBlockEntities;
import com.kuilunfuzhe.monvhua.features.portal.client.render.IndependentPortalRenderer;
import com.kuilunfuzhe.monvhua.network.portal.PortalPackets;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;

public final class PortalClient {
    private static boolean initialized;
    private static Object lastWorld;

    private PortalClient() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        BlockEntityRendererFactories.register(
                PortalBlockEntities.PORTAL_BLOCK_ENTITY,
                PortalBlockEntityRenderer::new
        );
        ClientPlayNetworking.registerGlobalReceiver(PortalPackets.OpenEditorS2C.ID, (packet, context) ->
                context.client().execute(() -> {
                    PortalFramebufferRenderer.requestPreviewCapture(packet.pos());
                    MinecraftClient.getInstance().setScreen(
                            new PortalGroupScreen(packet.pos(), packet.selectedGroup(), packet.groups(), packet.endpointCounts())
                    );
                }));
        ClientPlayNetworking.registerGlobalReceiver(PortalPackets.RemoteViewStateS2C.ID, (packet, context) ->
                context.client().execute(() -> PortalRemoteChunkCache.updateView(
                        packet.active(),
                        packet.sourcePos(),
                        packet.targetPos(),
                        packet.viewCenter(),
                        packet.radius(),
                        packet.generation()
                )));
        ClientPlayNetworking.registerGlobalReceiver(PortalPackets.RemoteChunkS2C.ID, (packet, context) ->
                context.client().execute(() ->
                        PortalRemoteChunkCache.loadRemoteChunkPacket(context.client().world, packet)));
        ClientPlayNetworking.registerGlobalReceiver(PortalPackets.RemoteHorizonS2C.ID, (packet, context) ->
                context.client().execute(() -> PortalHorizonCache.update(packet)));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Object currentWorld = client.world;
            if (currentWorld == null) {
                lastWorld = null;
                return;
            }
            if (lastWorld != null && lastWorld != currentWorld) {
                resetPortalClientState();
            }
            lastWorld = currentWorld;
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                client.execute(() -> {
                    resetPortalClientState();
                    lastWorld = null;
                }));
    }

    private static void resetPortalClientState() {
        PortalFramebufferRenderer.resetAll(true);
        PortalRemoteChunkCache.clear();
        PortalHorizonCache.clear();
        PortalHorizonRenderer.cleanup();
        IndependentPortalRenderer.cleanup();
    }
}
