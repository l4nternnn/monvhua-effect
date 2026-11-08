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
    public LocalCorpsePhysicsProvider(){service.initialize();}
    public int apiVersion(){return API_VERSION;}
    public String status(){return service.status()+"; pending="+service.pendingCount()+"; nativeMs="+(lastNativeNanos/1_000_000.0);}
    public CompletableFuture<PhysicsPoseDocument> solve(Request request) {
        var terrain=new TerrainSnapshotData(request.terrain().stream().map(b->new BoxScene.StaticBox(b.x0(),b.y0(),b.z0(),b.x1(),b.y1(),b.z1())).toList());
        var definition=request.slim()?RagdollDefinition.slim():RagdollDefinition.standard();
        // Feet remain at the sampled origin. The mesh inset supplies contact clearance.
        var input=new RagdollRequest(definition,terrain,new RagdollDefinition.V3(0,0,0),request.yaw(),INITIAL_LEAN_DEGREES,request.speed());
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
    public void close(){service.close();}
}
