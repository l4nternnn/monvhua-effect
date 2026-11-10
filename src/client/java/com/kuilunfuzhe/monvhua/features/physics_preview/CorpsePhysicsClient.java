package com.kuilunfuzhe.monvhua.features.physics_preview;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
/** Server owns physics lifetime, including integrated worlds. */
public final class CorpsePhysicsClient implements ClientModInitializer {
    public void onInitializeClient() {
        RemoteCorpsePhysics.initialize();LiveCorpseRenderer.initialize();CorpseGrabClient.initialize();
        ClientTickEvents.END_CLIENT_TICK.register(client->{RemoteCorpsePhysics.tick(client);CorpseControllerClient.tick(client);CorpseGrabClient.tick(client);});
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->RemoteCorpsePhysics.clear());
    }
}
