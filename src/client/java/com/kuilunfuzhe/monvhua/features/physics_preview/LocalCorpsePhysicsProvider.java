package com.kuilunfuzhe.monvhua.features.physics_preview;

import com.klfz.simplephysics.api.*;
import com.klfz.simplephysics.backend.PhysicsService;
import com.kuilunfuzhe.monvhua.features.forensics.physics.CorpsePhysicsBridge;
import java.util.LinkedHashMap;
import java.util.concurrent.CompletableFuture;

/** Loaded reflectively only when the optional mapping-independent development core is present. */
public final class LocalCorpsePhysicsProvider implements CorpsePhysicsBridge {
    private volatile long lastNativeNanos;
    private final PhysicsService service=new PhysicsService();
    public LocalCorpsePhysicsProvider(){
        // Resolve the new class before attaching: old optional cores fail closed.
        if(RagdollInitialMotion.class.getRecordComponents().length!=6)throw new LinkageError("Unsupported motion core");
        service.initialize();
    }
    public int apiVersion(){return API_VERSION;}
    public String status(){return service.status()+"; pending="+service.pendingCount()+"; solveElapsedMs="+(lastNativeNanos/1_000_000.0);}
    public CompletableFuture<PhysicsPoseDocument> solve(Request request) {
        var terrain=new TerrainSnapshotData(request.terrain().stream().map(b->new BoxScene.StaticBox(b.x0(),b.y0(),b.z0(),b.x1(),b.y1(),b.z1())).toList());
        var definition=request.slim()?RagdollDefinition.slim():RagdollDefinition.standard();
        // Feet remain at the sampled origin. The mesh inset supplies contact clearance.
        var input=input(request,definition,terrain);
        var nativeFuture=service.solve(input);var result=new CompletableFuture<PhysicsPoseDocument>();
        result.whenComplete((r,e)->{if(result.isCancelled())nativeFuture.cancel(true);});
        nativeFuture.whenComplete((r,error)->{
            if(error!=null){result.completeExceptionally(error);return;}
            lastNativeNanos=r.elapsedNanos();
            var poses=new LinkedHashMap<String,PhysicsPoseDocument.Pose>();
            for(var part:r.finalPose().parts()){var p=part.transform();poses.put(part.id(),new PhysicsPoseDocument.Pose(p.x(),p.y(),p.z(),p.qx(),p.qy(),p.qz(),p.qw()));}
            result.complete(new PhysicsPoseDocument(r.outcome().name(),r.finalPose().step(),poses,r.torsoTiltDegrees(),r.reason(),definition.id()+"_v1"));
        });
        return result;
    }
    public Session open(Request request) {
        var terrain=new TerrainSnapshotData(request.terrain().stream().map(b->new BoxScene.StaticBox(b.x0(),b.y0(),b.z0(),b.x1(),b.y1(),b.z1())).toList());
        var definition=request.slim()?RagdollDefinition.slim():RagdollDefinition.standard();
        var nativeSession=service.openRagdoll(input(request,definition,terrain));
        var future=new CompletableFuture<PhysicsPoseDocument>();
        future.whenComplete((r,e)->{if(future.isCancelled())nativeSession.close();});
        nativeSession.completion().whenComplete((r,e)->{
            if(e!=null)future.completeExceptionally(e);
            else {lastNativeNanos=r.elapsedNanos();future.complete(document(r.finalPose(),definition,r.outcome().name(),r.torsoTiltDegrees(),r.reason()));}
        });
        return new Session() {
            public void advanceTo(int step){nativeSession.advanceTo(step);}
            public java.util.List<PhysicsPoseDocument> frames(){return nativeSession.recentFrames().stream().map(f->document(f,definition,"SIMULATING",0,"live frame")).toList();}
            public CompletableFuture<PhysicsPoseDocument> completion(){return future;}
            public void close(){nativeSession.close();}
        };
    }
    private static PhysicsPoseDocument document(RagdollResult.Frame f,RagdollDefinition d,String outcome,float tilt,String reason) {
        var poses=new LinkedHashMap<String,PhysicsPoseDocument.Pose>();
        for(var part:f.parts()){var p=part.transform();poses.put(part.id(),new PhysicsPoseDocument.Pose(p.x(),p.y(),p.z(),p.qx(),p.qy(),p.qz(),p.qw()));}
        return new PhysicsPoseDocument(outcome,f.step(),poses,tilt,reason,d.id()+"_v1");
    }
    private static RagdollRequest input(Request request,RagdollDefinition definition,TerrainSnapshotData terrain) {
        var anchor=new RagdollDefinition.V3(0,0,0);var m=request.motion();
        return m==null?new RagdollRequest(definition,terrain,anchor,request.yaw(),INITIAL_LEAN_DEGREES,request.speed()):
            RagdollRequest.explicit(definition,terrain,anchor,RagdollInitialMotion.directed(request.yaw(),m.tilt(),m.dx(),m.dz(),new RagdollDefinition.V3(m.vx(),0,m.vz()),m.angularSpeed()));
    }
    public void close(){service.close();}
}
