package com.kuilunfuzhe.monvhua.features.forensics.physics;

import com.kuilunfuzhe.monvhua.features.physics_preview.PhysicsPoseDocument;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Optional local provider. No Minecraft or native types cross the worker boundary. */
public interface CorpsePhysicsBridge extends AutoCloseable {
    int API_VERSION=10;
    int ALGORITHM_VERSION=2;
    float INITIAL_LEAN_DEGREES=3;
    float DESTABILIZATION_SPEED=.3f;
    record Box(float x0,float y0,float z0,float x1,float y1,float z1) {}
    record Motion(float vx,float vz,float dx,float dz,float tilt,float angularSpeed) {}
    record Request(boolean slim,float yaw,float speed,List<Box> terrain,Motion motion,CorpsePhysicsProfiles.Profile profile,float scale,java.util.Set<String> parts) {
        public Request(boolean slim,float yaw,float speed,List<Box> terrain,Motion motion,CorpsePhysicsProfiles.Profile profile,float scale){this(slim,yaw,speed,terrain,motion,profile,scale,java.util.Set.copyOf(PhysicsPoseDocument.IDS));}
        public Request(boolean slim,float yaw,float speed,List<Box> terrain,Motion motion,CorpsePhysicsProfiles.Profile profile){this(slim,yaw,speed,terrain,motion,profile,1);}
        public Request(boolean slim,float yaw,float speed,List<Box> terrain,Motion motion){this(slim,yaw,speed,terrain,motion,motion==null?null:CorpsePhysicsProfiles.F5);}
        public Request(boolean slim,float yaw,float speed,List<Box> terrain){this(slim,yaw,speed,terrain,null);}
        public Request {parts=PhysicsPoseDocument.validateParts(parts);com.kuilunfuzhe.monvhua.features.physics_preview.CorpseScale.require(scale);terrain=List.copyOf(terrain);if(motion!=null)java.util.Objects.requireNonNull(profile);else if(profile!=null)throw new IllegalArgumentException("Legacy requests use the legacy profile");}
    }
    int apiVersion();
    String status();
    record Metrics(float anchor,float penetration,float limit){}
    default String diagnostic(Request request,PhysicsPoseDocument result,Metrics metrics,String stage,boolean reproduction){throw new UnsupportedOperationException("Diagnostic export unsupported");}
    CompletableFuture<PhysicsPoseDocument> solve(Request request);
    /** Null means this provider supports only the legacy batch path. */
    default Session open(Request request) {return null;}
    record TimedPose(long tick,PhysicsPoseDocument pose){}
    record ResidentState(String phase,long tick,long generation,long terrainRevision,boolean paused,PhysicsPoseDocument stable,String failure){}
    default Session openContinuous(Request request){throw new UnsupportedOperationException("Continuous core unavailable");}
    record Grab(long sequence,String part,float lx,float ly,float lz,float x,float y,float z,float ax,float ay,float az,boolean rotating,String dynamicPart,Support support) {
        public Grab(long sequence,String part,float lx,float ly,float lz,float x,float y,float z,float ax,float ay,float az,boolean rotating,String dynamicPart){this(sequence,part,lx,ly,lz,x,y,z,ax,ay,az,rotating,dynamicPart,null);}
    }
    record Support(float x,float y,float z,float w) {}
    default Session openSeeded(Request request,PhysicsPoseDocument pose){throw new UnsupportedOperationException("Seeded core unavailable");}
    record Velocity(float vx,float vy,float vz,float wx,float wy,float wz) {
        public Velocity{for(float v:new float[]{vx,vy,vz,wx,wy,wz})if(!Float.isFinite(v))throw new IllegalArgumentException("Nonfinite transfer velocity");}
    }
    record Transfer(PhysicsPoseDocument pose,java.util.Map<String,Velocity> velocities) {
        public Transfer {velocities=java.util.Map.copyOf(velocities);if(!velocities.keySet().equals(pose.poses().keySet()))throw new IllegalArgumentException("Incomplete transfer velocities");}
    }
    default Session prepare(Request request,Transfer state){throw new UnsupportedOperationException("Subset transfer core unavailable");}
    interface Session extends AutoCloseable {
        default CompletableFuture<Void> ready(){return CompletableFuture.completedFuture(null);}
        default CompletableFuture<Transfer> captureForTransfer(){return CompletableFuture.failedFuture(new UnsupportedOperationException("Frame transfer unavailable"));}
        default void releaseTransfer(){}

        default void control(Grab grab){throw new UnsupportedOperationException("Grab core unavailable");}
        default long controlSequence(){return 0;}
        default String dynamicPart(){return null;}
        default void region(List<Box> boxes,long revision,float x,float y,float z){throw new UnsupportedOperationException("Moving region unavailable");}
        default boolean continuous(){return false;}
        default ResidentState residentState(){return null;}
        default boolean resting(){return CorpseIdlePolicy.resting(residentState());}
        default void terrain(List<Box> boxes,long revision){throw new UnsupportedOperationException();}
        default void advanceClock(long tick){advanceTo(Math.toIntExact(tick));}
        default List<TimedPose> published(){return frames().stream().map(f->new TimedPose(f.step(),f)).toList();}
        default List<Long> historyTicks(){return historySteps().stream().map(Integer::longValue).toList();}
        default PhysicsPoseDocument poseAt(long tick){return frame(Math.toIntExact(tick));}
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
