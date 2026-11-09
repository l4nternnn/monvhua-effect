package com.kuilunfuzhe.monvhua.features.forensics.physics;

import com.kuilunfuzhe.monvhua.features.physics_preview.PhysicsPoseDocument;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Optional local provider. No Minecraft or native types cross the worker boundary. */
public interface CorpsePhysicsBridge extends AutoCloseable {
    int API_VERSION=4;
    int ALGORITHM_VERSION=2;
    float INITIAL_LEAN_DEGREES=3;
    float DESTABILIZATION_SPEED=.3f;
    record Box(float x0,float y0,float z0,float x1,float y1,float z1) {}
    record Motion(float vx,float vz,float dx,float dz,float tilt,float angularSpeed) {}
    record Request(boolean slim,float yaw,float speed,List<Box> terrain,Motion motion,CorpsePhysicsProfiles.Profile profile) {
        public Request(boolean slim,float yaw,float speed,List<Box> terrain,Motion motion){this(slim,yaw,speed,terrain,motion,motion==null?null:CorpsePhysicsProfiles.F5);}
        public Request(boolean slim,float yaw,float speed,List<Box> terrain){this(slim,yaw,speed,terrain,null);}
        public Request {terrain=List.copyOf(terrain);if(motion!=null)java.util.Objects.requireNonNull(profile);else if(profile!=null)throw new IllegalArgumentException("Legacy requests use the legacy profile");}
    }
    int apiVersion();
    String status();
    record Metrics(float anchor,float penetration,float limit){}
    default String diagnostic(Request request,PhysicsPoseDocument result,Metrics metrics,String stage,boolean reproduction){throw new UnsupportedOperationException("Diagnostic export unsupported");}
    CompletableFuture<PhysicsPoseDocument> solve(Request request);
    /** Null means this provider supports only the legacy batch path. */
    default Session open(Request request) {return null;}
    interface Session extends AutoCloseable {
        default Metrics metrics(){return new Metrics(0,0,0);}
        default boolean supportsHistory(){return false;}
        default int historyHz(){return 0;}
        default List<Integer> historySteps(){return List.of();}
        default void setPaused(boolean paused){throw new UnsupportedOperationException("History core unavailable");}
        default boolean paused(){return false;}
        default int firstFrame(){return -1;}
        default int lastFrame(){return -1;}
        default PhysicsPoseDocument frame(int step){return null;}
        default void historySeconds(int seconds){throw new UnsupportedOperationException("History core unavailable");}
        default void clearHistory(){}
        void advanceTo(int step);
        List<PhysicsPoseDocument> frames();
        CompletableFuture<PhysicsPoseDocument> completion();
        void close();
    }
    void close();
}
