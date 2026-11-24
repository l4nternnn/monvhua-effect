package com.kuilunfuzhe.monvhua.features.forensics.physics;
import com.kuilunfuzhe.monvhua.features.forensics.*;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Server resident ownership. Native state never lives on this thread. */
public final class CorpseResidentManager {
    private static final Map<UUID,Resident> objects=new ConcurrentHashMap<>();
    private static int cursor;private static long lastTick=Long.MIN_VALUE;private static String last="";
    private static final class Resident {
        final ItemDisplayEntity display;final ServerWorld world;final Vec3d origin;final CorpsePhysicsBridge.Session session;final boolean slim;final CorpsePhysicsBridge.Request input;final CorpseInitialMotionResolver.Motion motion;final long started=System.nanoTime();UUID victim;
        Vec3d region=Vec3d.ZERO;boolean manipulated;List<CorpsePhysicsBridge.Box> terrain;long terrainRevision,generation,progressTime,clock,sampledTick=Long.MIN_VALUE;boolean dirty;
        net.minecraft.nbt.NbtCompound visual;Vec3d position;String token;float yaw,pitch;org.joml.Matrix4f root;String context;
        Resident(ItemDisplayEntity d,Vec3d o,CorpsePhysicsBridge.Request input,CorpsePhysicsBridge.Session s,CorpseInitialMotionResolver.Motion motion){display=d;world=(ServerWorld)d.getWorld();origin=o;session=s;this.input=input;this.motion=motion;slim=input.slim();terrain=input.terrain();progressTime=world.getTime();refresh();}
        void refresh(){visual=EvidenceSnapshots.visualOnly(CorpsePoseRecord.data(display.getItemStack()));position=display.getPos();yaw=display.getYaw();pitch=display.getPitch();context=display.getItemDisplayContext().name();root=new org.joml.Matrix4f(com.kuilunfuzhe.monvhua.mixin.forensics.CorpseDisplayAccessor.monvhua$transform(display.getDataTracker()).getMatrix());token=Forensics.binding(display.getItemStack()).getString("token","");}
        boolean valid(){return !display.isRemoved()&&world.getEntity(display.getUuid())==display&&(!CorpseCarryService.isCarried(display)||CorpseTransportController.owns(display.getUuid(),session))&&position.equals(display.getPos())&&yaw==display.getYaw()&&pitch==display.getPitch()&&context.equals(display.getItemDisplayContext().name())&&root.equals(com.kuilunfuzhe.monvhua.mixin.forensics.CorpseDisplayAccessor.monvhua$transform(display.getDataTracker()).getMatrix(),1e-6f)&&visual.equals(EvidenceSnapshots.visualOnly(CorpsePoseRecord.data(display.getItemStack())))&&token.equals(Forensics.binding(display.getItemStack()).getString("token",""))&&Forensics.valid(world,display.getItemStack(),display.getUuid().toString());}
        boolean moving(){var s=session.residentState();return dirty||s==null||!s.phase().equals("SLEEPING");}
    }
    static boolean capacity(int extra){return objects.size()+KomDyingCompat.count()+extra<=16;}
    /** Transfers ownership of an existing living session; it must not be closed by its former owner. */
    static boolean adopt(ItemDisplayEntity display,Vec3d origin,CorpsePhysicsBridge.Request input,CorpsePhysicsBridge.Session session,UUID victim,long terrainRevision){
        if(!capacity(1)||!CorpseHistoryController.separationCapacity(1))return false;
        if(!commitDownedFrame(display,origin,input,session))return false;
        var r=new Resident(display,origin,input,session,null);r.victim=victim;r.terrainRevision=terrainRevision;
        var view=new CorpsePhysicsCoordinator.LiveView(display.getUuid(),r.world.getRegistryKey().getValue().toString(),origin,display.getItemStack().copy(),input.slim(),session,victim,0);
        if(!CorpseHistoryController.register(display,view))return false;
        objects.put(display.getUuid(),r);CorpsePhysicsCoordinator.publish(view);return true;
    }
    /** A frame from our own solver may be midair; do not require static ground support during ownership transfer. */
    static boolean commitDownedFrame(ItemDisplayEntity display,Vec3d origin,CorpsePhysicsBridge.Request input,CorpsePhysicsBridge.Session session){
        var frames=session.frames();if(frames.isEmpty())return false;
        var pose=frames.getLast();var data=CorpsePoseRecord.data(display.getItemStack());
        var record=new CorpsePoseRecord(data.getLong("corpse_pose_revision",0)+1,"generated",input.slim()?"slim":"default",pose.poses(),pose.definition(),pose.scale());
        data.put(CorpsePoseRecord.KEY,record.encode());data.putLong("corpse_pose_revision",record.revision());data.putString("arm_model",record.armModel());
        display.setTransformation(new net.minecraft.util.math.AffineTransformation(new org.joml.Matrix4f()));display.setYaw(0);display.setPitch(0);
        display.setItemDisplayContext(net.minecraft.item.ItemDisplayContext.NONE);display.setPosition(origin);
        CorpsePoseService.commitStatic(display,data);
        var store=ForensicStore.get(display.getWorld().getServer());var corpse=store.corpse(Forensics.binding(display.getItemStack()).getString("id",""));
        if(!corpse.contains("initial_generated_pose"))corpse.put("initial_generated_pose",record.encode());
        corpse.putString("last_pose_operation","kom_dying_handoff");store.markDirty();return true;
    }
    static boolean residentValid(UUID id){var r=objects.get(id);return r!=null&&r.valid();}
    static void installSeparation(ItemDisplayEntity parent,Vec3d parentOrigin,CorpsePhysicsBridge.Request parentInput,CorpsePhysicsBridge.Session parentSession,
                                  ItemDisplayEntity child,Vec3d childOrigin,CorpsePhysicsBridge.Request childInput,CorpsePhysicsBridge.Session childSession){
        var oldHistory=CorpseHistoryController.get(parent.getUuid());
        var a=new Resident(parent,parentOrigin,parentInput,parentSession,null);a.manipulated=true;
        var b=new Resident(child,childOrigin,childInput,childSession,null);b.manipulated=true;
        var av=new CorpsePhysicsCoordinator.LiveView(parent.getUuid(),a.world.getRegistryKey().getValue().toString(),parentOrigin,parent.getItemStack().copy(),parentInput.slim(),parentSession,null,0);
        var bv=new CorpsePhysicsCoordinator.LiveView(child.getUuid(),b.world.getRegistryKey().getValue().toString(),childOrigin,child.getItemStack().copy(),childInput.slim(),childSession,null,0);
        if(!CorpseHistoryController.register(parent,av)||!CorpseHistoryController.register(child,bv)){
            CorpseHistoryController.remove(parent.getUuid());CorpseHistoryController.remove(child.getUuid());
            if(oldHistory!=null&&oldHistory.view!=null)CorpseHistoryController.register(parent,oldHistory.view);
            throw new IllegalStateException("分离历史预算不足");
        }
        objects.put(parent.getUuid(),a);objects.put(child.getUuid(),b);
        CorpsePhysicsCoordinator.publish(av);CorpsePhysicsCoordinator.publish(bv);
    }
    public static void victim(UUID id,UUID victim){var r=objects.get(id);if(r!=null)r.victim=victim;}
    public static boolean contains(UUID id){return objects.containsKey(id);}
    public static boolean moving(UUID id){var r=objects.get(id);return r!=null&&(r.moving()||r.session.residentState().generation()>r.generation);}
    public static String status(){return "resident="+objects.size()+"/16 moving="+objects.values().stream().filter(Resident::moving).count()+" "+last;}
    static boolean open(ItemDisplayEntity display,Vec3d origin,CorpsePhysicsBridge.Request request,CorpsePhysicsBridge provider,CorpseInitialMotionResolver.Motion motion){
        if(!capacity(1)){CorpsePhysicsDiagnostics.rejected(CorpsePhysicsDiagnostics.Stage.CAPACITY);last="驻留容量已满，保留旧尸体";return false;}
        return open(display,origin,request,provider,motion,null);
    }
    static boolean open(ItemDisplayEntity display,Vec3d origin,CorpsePhysicsBridge.Request request,CorpsePhysicsBridge provider,CorpseInitialMotionResolver.Motion motion,com.kuilunfuzhe.monvhua.features.physics_preview.PhysicsPoseDocument seed){
        if(!capacity(1))throw new IllegalArgumentException("驻留容量已满");
        var session=seed==null?provider.openContinuous(request):provider.openSeeded(request,seed);
        try{
        var r=new Resident(display,origin,request,session,motion);objects.put(display.getUuid(),r);
        var view=new CorpsePhysicsCoordinator.LiveView(display.getUuid(),r.world.getRegistryKey().getValue().toString(),origin,display.getItemStack().copy(),request.slim(),session,null,0);
        CorpsePhysicsCoordinator.publish(view);
        if(!CorpseHistoryController.register(display,view)){remove(display.getUuid());last="历史预算不足";return false;}
        last="持续物理已接入";return true;
        }catch(RuntimeException|LinkageError error){session.close();objects.remove(display.getUuid());CorpsePhysicsCoordinator.unpublish(display.getUuid());CorpseHistoryController.remove(display.getUuid());throw error;}
    }
    public static boolean acceptsWounds(ItemDisplayEntity display){var r=objects.get(display.getUuid());return r==null||r.valid();}
    /** Only for the server's validated wound update, never a pose or ownership edit. */
    public static void woundsUpdated(ItemDisplayEntity display){
        var r=objects.get(display.getUuid());if(r==null)return;
        r.refresh();CorpseHistoryController.completed(display);
        CorpsePhysicsCoordinator.publish(new CorpsePhysicsCoordinator.LiveView(display.getUuid(),r.world.getRegistryKey().getValue().toString(),r.origin,display.getItemStack().copy(),r.slim,r.session,r.victim,CorpsePoseRecord.data(display.getItemStack()).getLong("corpse_pose_revision",0)));
    }
    public static void manipulated(UUID id){var r=objects.get(id);if(r!=null)r.manipulated=true;}
    public static void remove(UUID id){var r=objects.remove(id);if(r!=null)r.session.close();CorpsePhysicsCoordinator.unpublish(id);CorpseHistoryController.remove(id);}
    public static void reset(){for(var id:List.copyOf(objects.keySet()))remove(id);cursor=0;lastTick=Long.MIN_VALUE;last="";}
    public static boolean resume(UUID id){var r=objects.get(id);if(r==null)return false;try{if(!r.valid())throw new IllegalArgumentException("载体已改变");sample(r);return true;}catch(RuntimeException e){fail(r,e.getMessage());return false;}}
    private static void sample(Resident r){
        Vec3d center=r.region;
        if(r.manipulated){var frames=r.session.frames();if(!frames.isEmpty()){var p=frames.getLast().poses().get(frames.getLast().rootPart());var next=new Vec3d(Math.floor(p.x()),Math.floor(p.y()),Math.floor(p.z()));if(next.distanceTo(r.region)>1)center=next;}}
        if(center.length()>120)throw new IllegalArgumentException("已到本次物理空间 120 格移动边界，请放下后重新抓取");
        var terrain=CorpseTerrainSnapshot.capture(r.world,r.origin.add(center));r.sampledTick=r.world.getTime();
        if(!terrain.equals(r.terrain)||!center.equals(r.region)){r.terrain=terrain;r.region=center;r.dirty=true;r.session.region(terrain,++r.terrainRevision,(float)center.x,(float)center.y,(float)center.z);}
    }
    public static boolean wake(UUID id){var r=objects.get(id);if(r==null)return false;try{var terrain=CorpseTerrainSnapshot.capture(r.world,r.origin.add(r.region));r.terrain=terrain;r.dirty=true;r.session.region(terrain,++r.terrainRevision,(float)r.region.x,(float)r.region.y,(float)r.region.z);return true;}catch(RuntimeException e){fail(r,e.getMessage());return false;}}
    private static void fail(Resident r,String reason){reason=Objects.toString(reason,"unknown failure");last="持续模拟停止："+reason;org.slf4j.LoggerFactory.getLogger("monvhua-corpse-physics").warn(last);var stage=reason.startsWith("TIMEOUT")?CorpsePhysicsDiagnostics.Stage.SOLVER_TIMEOUT:reason.startsWith("LEFT_REGION")?CorpsePhysicsDiagnostics.Stage.LEFT_REGION:CorpsePhysicsDiagnostics.Stage.CONTINUOUS_STOPPED;CorpsePhysicsDiagnostics.rejected(stage);remove(r.display.getUuid());}
    public static void tick(){
        var all=List.copyOf(objects.values());
        if(all.isEmpty())return;long currentTick=all.getFirst().world.getTime();if(currentTick==lastTick)return;lastTick=currentTick;
        for(int i=0;i<Math.min(2,all.size());i++){var r=all.get(Math.floorMod(cursor++,all.size()));if(CorpseSeparationService.busy(r.display.getUuid()))continue;try{sample(r);}catch(RuntimeException e){fail(r,e.getMessage());}}
        for(var r:List.copyOf(objects.values()))try{
            if(CorpseSeparationService.busy(r.display.getUuid())){r.progressTime=r.world.getTime();continue;}
            if(!r.valid()){org.slf4j.LoggerFactory.getLogger("monvhua-corpse-physics").info("Resident carrier invalidated: removed={} carried={} position={} visual={} token={}",r.display.isRemoved(),CorpseCarryService.isCarried(r.display),r.position.equals(r.display.getPos()),r.visual.equals(EvidenceSnapshots.visualOnly(CorpsePoseRecord.data(r.display.getItemStack()))),r.token.equals(Forensics.binding(r.display.getItemStack()).getString("token","")));remove(r.display.getUuid());continue;}
            if(CorpseHistoryController.get(r.display.getUuid())==null){fail(r,"历史载体已改变或丢失");continue;}
            boolean held=CorpseGrabController.held(r.display.getUuid())||CorpseTransportController.held(r.display.getUuid());
            if(held&&r.sampledTick!=r.world.getTime()&&!r.session.paused())sample(r);
            var state=r.session.residentState();
            if(state.phase().equals("FAILED")||state.phase().equals("CLOSED")){fail(r,state.failure());continue;}
            long now=r.world.getTime();if(state.tick()!=r.clock||state.paused()||CorpseIdlePolicy.resting(state)){r.clock=state.tick();r.progressTime=now;}
            if(now-r.progressTime>60){fail(r,"无模拟进展");continue;}
            if(state.terrainRevision()==r.terrainRevision)r.dirty=false;
            if(CorpseIdlePolicy.advance(state,held,r.dirty)){
                r.session.advanceClock(state.tick()+3);CorpsePerformance.count(CorpsePerformance.Counter.PHYSICS_ADVANCED);
            }else if(CorpseIdlePolicy.resting(state))CorpsePerformance.count(CorpsePerformance.Counter.PHYSICS_IDLE_SKIPPED);
            if(!r.moving()&&state.generation()>r.generation){
                // Commit only on this object's scheduled capture tick, within the two-captures budget.
                if(r.sampledTick!=now||r.dirty)continue;
                var pose=state.stable();var normalized=new java.util.LinkedHashMap<String,com.kuilunfuzhe.monvhua.features.physics_preview.PhysicsPoseDocument.Pose>();
                for(var part:pose.poses().entrySet()){var p=part.getValue();normalized.put(part.getKey(),new com.kuilunfuzhe.monvhua.features.physics_preview.PhysicsPoseDocument.Pose(p.x()-(float)r.region.x,p.y()-(float)r.region.y,p.z()-(float)r.region.z,p.qx(),p.qy(),p.qz(),p.qw()));}
                var old=CorpsePoseRecord.read(CorpsePoseRecord.data(r.display.getItemStack()));
                if(old.isEmpty()||!old.get().parts().equals(normalized)||!r.display.getPos().equals(r.origin.add(r.region))){
                    long revision=CorpsePoseRecord.data(r.display.getItemStack()).getLong("corpse_pose_revision",0)+1;
                    CorpsePoseService.applyGenerated(r.display,new CorpsePoseRecord(revision,"generated",r.slim?"slim":"default",normalized,pose.definition(),pose.scale()),r.origin.add(r.region));
                }
                if(r.generation==0&&r.terrainRevision==0)CorpsePhysicsDiagnostics.record(CorpsePhysicsDiagnostics.Stage.APPLIED,r.input,pose,r.session.metrics(),System.nanoTime()-r.started);
                var store=ForensicStore.get(r.world.getServer());var corpse=store.corpse(Forensics.binding(r.display.getItemStack()).getString("id",""));
                if(r.generation==0&&r.motion!=null){
                    var m=r.motion;var diagnostics=new net.minecraft.nbt.NbtCompound();
                    diagnostics.putString("motion_source",m.source());diagnostics.putString("direction_quality",m.quality());diagnostics.putString("incoming",m.incoming());diagnostics.putString("motion_fallback",m.fallback());
                    diagnostics.putFloat("vx",m.vx());diagnostics.putFloat("vz",m.vz());diagnostics.putString("profile_id",m.profile().id());diagnostics.putString("profile_hash",m.profile().hash());diagnostics.putString("profile_snapshot",new com.google.gson.Gson().toJson(m.profile()));
                    diagnostics.putString("run_policy","CONTINUOUS");diagnostics.putInt("algorithm_version",CorpsePhysicsBridge.ALGORITHM_VERSION);corpse.put("pose_generation_diagnostics",diagnostics);
                }
                corpse.putLong("continuous_generation",state.generation());corpse.putLong("continuous_tick",state.tick());store.markDirty();
                r.generation=state.generation();r.refresh();CorpseHistoryController.completed(r.display);var h=CorpseHistoryController.get(r.display.getUuid());if(h!=null){var v=h.view;CorpsePhysicsCoordinator.publish(new CorpsePhysicsCoordinator.LiveView(v.display(),v.dimension(),v.origin(),r.display.getItemStack().copy(),v.slim(),v.session(),r.victim,CorpsePoseRecord.data(r.display.getItemStack()).getLong("corpse_pose_revision",0)));}last="休眠 generation="+r.generation+" tick="+state.tick();
            }else if(r.moving()&&!CorpseHistoryController.paused(r.display.getUuid())){
                var history=CorpseHistoryController.get(r.display.getUuid());if(history!=null){var v=history.view;CorpsePhysicsCoordinator.publish(new CorpsePhysicsCoordinator.LiveView(v.display(),v.dimension(),v.origin(),v.skin(),v.slim(),v.session(),r.victim,0));}
            }
        }catch(RuntimeException e){fail(r,e.getMessage());}
    }
}
