package com.kuilunfuzhe.monvhua.features.forensics.physics;

import com.kuilunfuzhe.monvhua.features.physics_preview.PhysicsPoseDocument;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Optional local provider. No Minecraft or native types cross the worker boundary. */
public interface CorpsePhysicsBridge extends AutoCloseable {
    int API_VERSION=2;
    int ALGORITHM_VERSION=2;
    float INITIAL_LEAN_DEGREES=3;
    float DESTABILIZATION_SPEED=.3f;
    record Box(float x0,float y0,float z0,float x1,float y1,float z1) {}
    record Motion(float vx,float vz,float dx,float dz,float tilt,float angularSpeed) {}
    record Request(boolean slim,float yaw,float speed,List<Box> terrain,Motion motion) {
        public Request(boolean slim,float yaw,float speed,List<Box> terrain){this(slim,yaw,speed,terrain,null);}
        public Request {terrain=List.copyOf(terrain);}
    }
    int apiVersion();
    String status();
    CompletableFuture<PhysicsPoseDocument> solve(Request request);
    /** Null means this provider supports only the legacy batch path. */
    default Session open(Request request) {return null;}
    interface Session extends AutoCloseable {
        void advanceTo(int step);
        List<PhysicsPoseDocument> frames();
        CompletableFuture<PhysicsPoseDocument> completion();
        void close();
    }
    void close();
}
