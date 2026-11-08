package com.kuilunfuzhe.monvhua.features.physics_preview;

import com.kuilunfuzhe.monvhua.features.forensics.physics.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/** Lifetime belongs to the local integrated world, never a remote server connection. */
public final class CorpsePhysicsClient implements ClientModInitializer {
    private Object owner;
    public void onInitializeClient() {
        LiveCorpseRenderer.initialize();
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            CorpseControllerClient.tick(client);
            var server=client.world==null?null:client.getServer();
            if(owner==server)return;
            CorpsePhysicsCoordinator.detach();owner=server;
            if(server==null)return;
            try {
                var provider=(CorpsePhysicsBridge)Class.forName("com.kuilunfuzhe.monvhua.features.physics_preview.LocalCorpsePhysicsProvider").getConstructor().newInstance();
                CorpsePhysicsCoordinator.attach(server,provider);
            }catch(ReflectiveOperationException|LinkageError e){CorpsePhysicsCoordinator.unavailable("物理核心缺失或版本不匹配："+e.getClass().getSimpleName());}
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{owner=null;CorpsePhysicsCoordinator.detach();});
        ClientLifecycleEvents.CLIENT_STOPPING.register(client->CorpsePhysicsCoordinator.detach());
    }
}
