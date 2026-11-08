package com.kuilunfuzhe.monvhua.features.forensics.physics;

import com.kuilunfuzhe.monvhua.features.physics_preview.PhysicsPoseDocument;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Optional local provider. No Minecraft or native types cross the worker boundary. */
public interface CorpsePhysicsBridge extends AutoCloseable {
    int API_VERSION=1;
    int ALGORITHM_VERSION=1;
    float INITIAL_LEAN_DEGREES=3;
    float DESTABILIZATION_SPEED=.3f;
    record Box(float x0,float y0,float z0,float x1,float y1,float z1) {}
    record Request(boolean slim,float yaw,float speed,List<Box> terrain) {
        public Request {terrain=List.copyOf(terrain);}
    }
    int apiVersion();
    String status();
    CompletableFuture<PhysicsPoseDocument> solve(Request request);
    void close();
}
