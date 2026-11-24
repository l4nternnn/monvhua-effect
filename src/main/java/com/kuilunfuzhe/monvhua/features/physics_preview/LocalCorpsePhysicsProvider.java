package com.kuilunfuzhe.monvhua.features.physics_preview;

import com.klfz.simplephysics.api.*;
import com.klfz.simplephysics.backend.PhysicsService;
import com.kuilunfuzhe.monvhua.features.forensics.physics.CorpsePhysicsBridge;
import com.kuilunfuzhe.monvhua.features.forensics.physics.CorpsePhysicsProfiles;
import java.util.LinkedHashMap;
import java.util.concurrent.CompletableFuture;

/** Side-independent physics adapter. Loaded reflectively when the compatible physics core is present. */
public final class LocalCorpsePhysicsProvider implements CorpsePhysicsBridge {
    private volatile long lastNativeNanos;
    private final PhysicsService service=new PhysicsService();
    public LocalCorpsePhysicsProvider(){
        // Resolve scale support before attaching: old optional cores fail closed.
        try {RagdollDefinition.class.getMethod("standard",float.class); RagdollDefinition.class.getMethod("scale");RagdollDefinition.class.getMethod("subset",java.util.Set.class); PhysicsService.class.getMethod("prepareContinuous",RagdollRequest.class,RagdollMotionState.class);}
        catch(NoSuchMethodException e){throw new LinkageError("Unsupported scale core",e);}
        if(RagdollMotionState.TRANSFER_VERSION!=1)throw new LinkageError("Unsupported transfer core");
        if(RagdollInitialMotion.class.getRecordComponents().length!=6)throw new LinkageError("Unsupported motion core");
        if(RagdollRequest.class.getRecordComponents().length!=8||!solver(CorpsePhysicsProfiles.F5).equals(RagdollSolverProfile.F5))throw new LinkageError("Unsupported profile core/baseline");
        if(RagdollDiagnosticDocument.Document.class.getRecordComponents().length!=13)throw new LinkageError("Unsupported diagnostic core");
        if(com.klfz.simplephysics.backend.ContinuousRagdollSession.Snapshot.class.getRecordComponents().length!=7)throw new LinkageError("Unsupported continuous core");
        try {com.klfz.simplephysics.backend.ContinuousRagdollSession.class.getMethod("control",RagdollControl.class);}
        catch(NoSuchMethodException e){throw new LinkageError("Unsupported grab core",e);}
        try{var field=RagdollControl.class.getField("CONTROL_VERSION");if(field.getInt(null)!=2||RagdollControl.class.getField("SUPPORT_VERSION").getInt(null)!=1)throw new LinkageError("Unsupported limb slot core");}
        catch(ReflectiveOperationException e){throw new LinkageError("Unsupported limb slot core",e);}
        service.initialize().thenAccept(status->{
            var log=org.slf4j.LoggerFactory.getLogger("monvhua-corpse-physics");
            if(status.startsWith("Ready:"))log.info("Native physics: {}",status);else log.error("Native physics: {}; static corpses remain available",status);
        });
    }
    public int apiVersion(){return API_VERSION;}
    public String diagnostic(Request request,PhysicsPoseDocument result,Metrics metrics,String stage,boolean reproduction){
        var terrain=new TerrainSnapshotData(request.terrain().stream().map(b->new BoxScene.StaticBox(b.x0(),b.y0(),b.z0(),b.x1(),b.y1(),b.z1())).toList());
        var definition=(request.slim()?RagdollDefinition.slim(request.scale()):RagdollDefinition.standard(request.scale())).subset(request.parts());
        RagdollResult.Frame frame=result==null?null:new RagdollResult.Frame(result.step(),result.poses().keySet().stream().map(id->{var p=result.poses().get(id);return new RagdollResult.Pose(id,new BoxTrace.Frame(p.x(),p.y(),p.z(),p.qx(),p.qy(),p.qz(),p.qw()));}).toList());
        // Keep diagnostic part order identical to the pinned solver definition for exact replay equality.
        if(frame!=null){var byId=new java.util.HashMap<String,RagdollResult.Pose>();for(var p:frame.parts())byId.put(p.id(),p);frame=new RagdollResult.Frame(frame.step(),definition.parts().stream().map(p->byId.get(p.id())).toList());}
        var value=new RagdollDiagnosticDocument.Result(result==null?"NO_RESULT":result.outcome(),frame,metrics.anchor(),metrics.penetration(),metrics.limit(),result==null?stage:result.reason());
        return RagdollDiagnosticDocument.encode(RagdollDiagnosticDocument.capture(input(request,definition,terrain),value,stage,reproduction));
    }
    public String status(){return service.status()+"; pending="+service.pendingCount()+"; solveElapsedMs="+(lastNativeNanos/1_000_000.0);}
    public CompletableFuture<PhysicsPoseDocument> solve(Request request) {
        var terrain=new TerrainSnapshotData(request.terrain().stream().map(b->new BoxScene.StaticBox(b.x0(),b.y0(),b.z0(),b.x1(),b.y1(),b.z1())).toList());
        var definition=(request.slim()?RagdollDefinition.slim(request.scale()):RagdollDefinition.standard(request.scale())).subset(request.parts());
        // Feet remain at the sampled origin. The mesh inset supplies contact clearance.
        var input=input(request,definition,terrain);
        var nativeFuture=service.solve(input);var result=new CompletableFuture<PhysicsPoseDocument>();
        result.whenComplete((r,e)->{if(result.isCancelled())nativeFuture.cancel(true);});
        nativeFuture.whenComplete((r,error)->{
            if(error!=null){result.completeExceptionally(error);return;}
            lastNativeNanos=r.elapsedNanos();
            var poses=new LinkedHashMap<String,PhysicsPoseDocument.Pose>();
            for(var part:r.finalPose().parts()){var p=part.transform();poses.put(part.id(),new PhysicsPoseDocument.Pose(p.x(),p.y(),p.z(),p.qx(),p.qy(),p.qz(),p.qw()));}
            result.complete(new PhysicsPoseDocument(r.outcome().name(),r.finalPose().step(),poses,r.torsoTiltDegrees(),r.reason(),definition.id()+"_v1",definition.scale()));
        });
        return result;
    }
    public Session openContinuous(Request request){return openSeeded(request,null);}
    public Session openSeeded(Request request,PhysicsPoseDocument pose){return resident(request,pose,null);}
    public Session prepare(Request request,Transfer state){return resident(request,state.pose(),state);}
    private Session resident(Request request,PhysicsPoseDocument pose,Transfer transfer){
        var definition=(request.slim()?RagdollDefinition.slim(request.scale()):RagdollDefinition.standard(request.scale())).subset(request.parts());
        RagdollResult.Frame seed=pose==null?null:new RagdollResult.Frame(0,definition.parts().stream().map(part->{var p=pose.poses().get(part.id());return new RagdollResult.Pose(part.id(),new BoxTrace.Frame(p.x(),p.y(),p.z(),p.qx(),p.qy(),p.qz(),p.qw()));}).toList());
        var requestInput=input(request,definition,terrain(request.terrain()));
        if(seed!=null)requestInput=RagdollRequest.explicit(definition,terrain(request.terrain()),new RagdollDefinition.V3(0,0,0),RagdollInitialMotion.directed(0,0,0,1,new RagdollDefinition.V3(0,0,0),0));
        var session=transfer==null?service.openContinuous(requestInput,seed):service.prepareContinuous(requestInput,nativeState(transfer,definition));
        return new Session(){
            public CompletableFuture<Void> ready(){return session.ready();}
            public void releaseTransfer(){session.releaseTransfer();}
            public CompletableFuture<Transfer> captureForTransfer(){return session.captureForTransfer().thenApply(value->{
                var velocities=new LinkedHashMap<String,Velocity>();value.velocities().forEach((id,v)->velocities.put(id,new Velocity(v.linear().x(),v.linear().y(),v.linear().z(),v.angular().x(),v.angular().y(),v.angular().z())));
                return new Transfer(document(value.pose(),definition,"SIMULATING",0,"transfer"),velocities);
            });}

            private final CompletableFuture<PhysicsPoseDocument> completion=session.completion().thenApply(v->null);
            private RagdollResult stableNative;private PhysicsPoseDocument stablePose;
            // The core publishes immutable frame lists. Convert only when its publication identity changes.
            private java.util.List<com.klfz.simplephysics.backend.ContinuousRagdollSession.Frame> convertedSource;
            private java.util.List<TimedPose> convertedFrames=java.util.List.of();
            private java.util.List<PhysicsPoseDocument> convertedPoses=java.util.List.of();
            public boolean continuous(){return true;}
            public void control(Grab g){session.control(new RagdollControl(g.sequence(),g.part(),new RagdollDefinition.V3(g.lx(),g.ly(),g.lz()),new RagdollDefinition.V3(g.x(),g.y(),g.z()),new RagdollDefinition.V3(g.ax(),g.ay(),g.az()),g.rotating(),g.dynamicPart(),g.support()==null?null:new RagdollControl.Support(g.support().x(),g.support().y(),g.support().z(),g.support().w())));}
            public long controlSequence(){return session.controlSequence();}
            public String dynamicPart(){return session.dynamicPart();}
            public void region(java.util.List<Box> boxes,long revision,float x,float y,float z){session.terrain(LocalCorpsePhysicsProvider.terrain(boxes),revision,new RagdollDefinition.V3(x,y,z));}
            public Metrics metrics(){var r=session.snapshot().stable();return r==null?new Metrics(0,0,0):new Metrics(r.maxAnchorError(),r.maxPenetration(),r.maxLimitError());}
            public synchronized ResidentState residentState(){var s=session.snapshot();if(stableNative!=s.stable()){stableNative=s.stable();stablePose=stableNative==null?null:document(stableNative.finalPose(),definition,"SETTLED",stableNative.torsoTiltDegrees(),stableNative.reason());}return new ResidentState(s.phase().name(),s.tick(),s.generation(),s.terrainRevision(),s.paused(),stablePose,s.failure());}
            public void terrain(java.util.List<Box> boxes,long revision){session.terrain(LocalCorpsePhysicsProvider.terrain(boxes),revision);}
            public void advanceClock(long tick){session.advanceTo(tick);}
            public void advanceTo(int step){session.advanceTo(step);}
            public boolean supportsHistory(){return true;}
            public int historyHz(){return 12;}
            public java.util.List<Long> historyTicks(){return session.historyTicks();}
            public PhysicsPoseDocument poseAt(long tick){var f=session.frame(tick);return f==null?null:document(f.pose(),definition,"SIMULATING",0,"continuous history");}
            public synchronized java.util.List<TimedPose> published(){
                var source=session.frames();
                if(source!=convertedSource){
                    convertedFrames=source.stream().map(f->new TimedPose(f.tick(),document(f.pose(),definition,"SIMULATING",0,"continuous"))).toList();
                    convertedPoses=convertedFrames.stream().map(TimedPose::pose).toList();convertedSource=source;
                }
                return convertedFrames;
            }
            public synchronized java.util.List<PhysicsPoseDocument> frames(){published();return convertedPoses;}
            public void setPaused(boolean value){session.setPaused(value);}
            public boolean paused(){return session.snapshot().paused();}
            public void historySeconds(int seconds){session.historySeconds(seconds);}
            public void clearHistory(){session.clearHistory();}
            public CompletableFuture<PhysicsPoseDocument> completion(){return completion;}
            public void close(){session.close();}
        };
    }
    private static RagdollMotionState nativeState(Transfer transfer,RagdollDefinition definition){
        var velocities=new LinkedHashMap<String,RagdollMotionState.Velocity>();transfer.velocities().forEach((id,v)->velocities.put(id,new RagdollMotionState.Velocity(new RagdollDefinition.V3(v.vx(),v.vy(),v.vz()),new RagdollDefinition.V3(v.wx(),v.wy(),v.wz()))));
        var parts=definition.parts().stream().map(part->{var p=transfer.pose().poses().get(part.id());return new RagdollResult.Pose(part.id(),new BoxTrace.Frame(p.x(),p.y(),p.z(),p.qx(),p.qy(),p.qz(),p.qw()));}).toList();
        return new RagdollMotionState(new RagdollResult.Frame(0,parts),velocities);
    }
    private static TerrainSnapshotData terrain(java.util.List<Box> boxes){return new TerrainSnapshotData(boxes.stream().map(b->new BoxScene.StaticBox(b.x0(),b.y0(),b.z0(),b.x1(),b.y1(),b.z1())).toList());}
    public Session open(Request request) {
        var terrain=new TerrainSnapshotData(request.terrain().stream().map(b->new BoxScene.StaticBox(b.x0(),b.y0(),b.z0(),b.x1(),b.y1(),b.z1())).toList());
        var definition=(request.slim()?RagdollDefinition.slim(request.scale()):RagdollDefinition.standard(request.scale())).subset(request.parts());
        var nativeSession=service.openRagdoll(input(request,definition,terrain));
        var future=new CompletableFuture<PhysicsPoseDocument>();
        future.whenComplete((r,e)->{if(future.isCancelled())nativeSession.close();});
        nativeSession.completion().whenComplete((r,e)->{
            if(e!=null)future.completeExceptionally(e);
            else {lastNativeNanos=r.elapsedNanos();future.complete(document(r.finalPose(),definition,r.outcome().name(),r.torsoTiltDegrees(),r.reason()));}
        });
        return new Session() {
            public Metrics metrics(){
                if(!nativeSession.completion().isDone()||nativeSession.completion().isCompletedExceptionally())return new Metrics(0,0,0);
                var r=nativeSession.completion().getNow(null);return r==null?new Metrics(0,0,0):new Metrics(r.maxAnchorError(),r.maxPenetration(),r.maxLimitError());
            }
            private java.util.List<RagdollResult.Frame> sampledNative=java.util.List.of();
            private java.util.List<PhysicsPoseDocument> sampledDocuments=java.util.List.of();
            public boolean supportsHistory(){try{nativeSession.getClass().getMethod("history").getReturnType().getMethod("sampleSteps");return true;}catch(NoSuchMethodException e){return false;}}
            public int historyHz(){return 12;}
            public java.util.List<Integer> historySteps(){return nativeSession.history().sampleSteps();}
            public void setPaused(boolean value){nativeSession.setPaused(value);}
            public boolean paused(){return nativeSession.isPaused();}
            public int firstFrame(){return nativeSession.history().first();}
            public int lastFrame(){return nativeSession.history().last();}
            public PhysicsPoseDocument frame(int step){var f=nativeSession.history().frame(step);return f==null?null:document(f,definition,"SIMULATING",0,"history frame");}
            public void historySeconds(int seconds){nativeSession.history().resize(seconds);}
            public void clearHistory(){nativeSession.history().clear();}
            public void advanceTo(int step){nativeSession.advanceTo(step);}
            public synchronized java.util.List<PhysicsPoseDocument> frames(){
                var batch=nativeSession.recentFrames();if(batch==sampledNative)return sampledDocuments;
                var previous=new java.util.HashMap<Integer,PhysicsPoseDocument>();for(var d:sampledDocuments)previous.put(d.step(),d);
                sampledDocuments=batch.stream().map(f->previous.containsKey(f.step())?previous.get(f.step()):document(f,definition,"SIMULATING",0,"live frame")).toList();
                sampledNative=batch;return sampledDocuments;
            }
            public CompletableFuture<PhysicsPoseDocument> completion(){return future;}
            public void close(){nativeSession.close();}
        };
    }
    private static PhysicsPoseDocument document(RagdollResult.Frame f,RagdollDefinition d,String outcome,float tilt,String reason) {
        var poses=new LinkedHashMap<String,PhysicsPoseDocument.Pose>();
        for(var part:f.parts()){var p=part.transform();poses.put(part.id(),new PhysicsPoseDocument.Pose(p.x(),p.y(),p.z(),p.qx(),p.qy(),p.qz(),p.qw()));}
        return new PhysicsPoseDocument(outcome,f.step(),poses,tilt,reason,d.id()+"_v1",d.scale());
    }
    private static RagdollRequest input(Request request,RagdollDefinition definition,TerrainSnapshotData terrain) {
        var anchor=new RagdollDefinition.V3(0,0,0);var m=request.motion();
        return m==null?new RagdollRequest(definition,terrain,anchor,request.yaw(),INITIAL_LEAN_DEGREES,request.speed()):
            RagdollRequest.explicit(definition,terrain,anchor,RagdollInitialMotion.directed(request.yaw(),m.tilt(),m.dx(),m.dz(),new RagdollDefinition.V3(m.vx(),0,m.vz()),m.angularSpeed())).withProfile(solver(request.profile()));
    }
    private static RagdollSolverProfile solver(CorpsePhysicsProfiles.Profile p){var s=p.solver();return new RagdollSolverProfile(p.id(),s.iterations(),s.linearDamping(),s.angularDamping(),s.friction(),s.rollingFriction(),s.spinningFriction());}
    public void close(){service.close();}
}
