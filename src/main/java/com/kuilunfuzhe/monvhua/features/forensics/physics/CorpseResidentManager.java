package com.kuilunfuzhe.monvhua.features.forensics.physics;
import com.kuilunfuzhe.monvhua.features.forensics.*;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Integrated-server resident ownership. Native state never lives on this thread. */
public final class CorpseResidentManager {
    private static final Map<UUID,Resident> objects=new ConcurrentHashMap<>();
    private static int cursor;private static long lastTick=Long.MIN_VALUE;private static String last="";
    private static final class Resident {
        final ItemDisplayEntity display;final ServerWorld world;final Vec3d origin;final CorpsePhysicsBridge.Session session;final boolean slim;final CorpsePhysicsBridge.Request input;final CorpseInitialMotionResolver.Motion motion;final long started=System.nanoTime();UUID victim;
        List<CorpsePhysicsBridge.Box> terrain;long terrainRevision,generation,progressTime,clock,sampledTick=Long.MIN_VALUE;boolean dirty;
        net.minecraft.nbt.NbtCompound visual;Vec3d position;String token;float yaw,pitch;org.joml.Matrix4f root;String context;
        Resident(ItemDisplayEntity d,Vec3d o,CorpsePhysicsBridge.Request input,CorpsePhysicsBridge.Session s,CorpseInitialMotionResolver.Motion motion){display=d;world=(ServerWorld)d.getWorld();origin=o;session=s;this.input=input;this.motion=motion;slim=input.slim();terrain=input.terrain();progressTime=world.getTime();refresh();}
        void refresh(){visual=EvidenceSnapshots.visualOnly(CorpsePoseRecord.data(display.getItemStack()));position=display.getPos();yaw=display.getYaw();pitch=display.getPitch();context=display.getItemDisplayContext().name();root=new org.joml.Matrix4f(com.kuilunfuzhe.monvhua.mixin.forensics.CorpseDisplayAccessor.monvhua$transform(display.getDataTracker()).getMatrix());token=Forensics.binding(display.getItemStack()).getString("token","");}
        boolean valid(){return !display.isRemoved()&&world.getEntity(display.getUuid())==display&&!CorpseCarryService.isCarried(display)&&position.equals(display.getPos())&&yaw==display.getYaw()&&pitch==display.getPitch()&&context.equals(display.getItemDisplayContext().name())&&root.equals(com.kuilunfuzhe.monvhua.mixin.forensics.CorpseDisplayAccessor.monvhua$transform(display.getDataTracker()).getMatrix(),1e-6f)&&visual.equals(EvidenceSnapshots.visualOnly(CorpsePoseRecord.data(display.getItemStack())))&&token.equals(Forensics.binding(display.getItemStack()).getString("token",""))&&Forensics.valid(world,display.getItemStack(),display.getUuid().toString());}
        boolean moving(){var s=session.residentState();return dirty||s==null||!s.phase().equals("SLEEPING");}
    }
    public static void victim(UUID id,UUID victim){var r=objects.get(id);if(r!=null)r.victim=victim;}
    public static boolean contains(UUID id){return objects.containsKey(id);}
    public static boolean moving(UUID id){var r=objects.get(id);return r!=null&&(r.moving()||r.session.residentState().generation()>r.generation);}
    public static String status(){return "resident="+objects.size()+"/16 moving="+objects.values().stream().filter(Resident::moving).count()+" "+last;}
    static boolean open(ItemDisplayEntity display,Vec3d origin,CorpsePhysicsBridge.Request request,CorpsePhysicsBridge provider,CorpseInitialMotionResolver.Motion motion){
        if(objects.size()>=16){CorpsePhysicsDiagnostics.rejected(CorpsePhysicsDiagnostics.Stage.CAPACITY);last="驻留容量已满，保留旧尸体";return false;}
        var session=provider.openContinuous(request);
        try{
        var r=new Resident(display,origin,request,session,motion);objects.put(display.getUuid(),r);
        var view=new CorpsePhysicsCoordinator.LiveView(display.getUuid(),r.world.getRegistryKey().getValue().toString(),origin,display.getItemStack().copy(),request.slim(),session,null,0);
        CorpsePhysicsCoordinator.publish(view);
        if(!CorpseHistoryController.register(display,view)){remove(display.getUuid());last="历史预算不足";return false;}
        last="持续物理已接入";return true;
        }catch(RuntimeException|LinkageError error){session.close();objects.remove(display.getUuid());CorpsePhysicsCoordinator.unpublish(display.getUuid());CorpseHistoryController.remove(display.getUuid());throw error;}
    }
    public static void remove(UUID id){var r=objects.remove(id);if(r!=null)r.session.close();CorpsePhysicsCoordinator.unpublish(id);CorpseHistoryController.remove(id);}
    public static void reset(){for(var id:List.copyOf(objects.keySet()))remove(id);cursor=0;lastTick=Long.MIN_VALUE;last="";}
    public static boolean resume(UUID id){var r=objects.get(id);if(r==null)return false;try{if(!r.valid())throw new IllegalArgumentException("载体已改变");sample(r);return true;}catch(RuntimeException e){fail(r,e.getMessage());return false;}}
    private static void sample(Resident r){var terrain=CorpseTerrainSnapshot.capture(r.world,r.origin);r.sampledTick=r.world.getTime();if(!terrain.equals(r.terrain)){r.terrain=terrain;r.dirty=true;r.session.terrain(terrain,++r.terrainRevision);}}
    public static boolean wake(UUID id){var r=objects.get(id);if(r==null)return false;try{var terrain=CorpseTerrainSnapshot.capture(r.world,r.origin);r.terrain=terrain;r.dirty=true;r.session.terrain(terrain,++r.terrainRevision);return true;}catch(RuntimeException e){fail(r,e.getMessage());return false;}}
    private static void fail(Resident r,String reason){reason=Objects.toString(reason,"unknown failure");last="持续模拟停止："+reason;org.slf4j.LoggerFactory.getLogger("monvhua-corpse-physics").warn(last);var stage=reason.startsWith("TIMEOUT")?CorpsePhysicsDiagnostics.Stage.SOLVER_TIMEOUT:reason.startsWith("LEFT_REGION")?CorpsePhysicsDiagnostics.Stage.LEFT_REGION:CorpsePhysicsDiagnostics.Stage.CONTINUOUS_STOPPED;CorpsePhysicsDiagnostics.rejected(stage);remove(r.display.getUuid());}
    public static void tick(){
        var all=List.copyOf(objects.values());
        if(all.isEmpty())return;long currentTick=all.getFirst().world.getTime();if(currentTick==lastTick)return;lastTick=currentTick;
        for(int i=0;i<Math.min(2,all.size());i++){var r=all.get(Math.floorMod(cursor++,all.size()));try{sample(r);}catch(RuntimeException e){fail(r,e.getMessage());}}
        for(var r:List.copyOf(objects.values()))try{
            if(!r.valid()){org.slf4j.LoggerFactory.getLogger("monvhua-corpse-physics").info("Resident carrier invalidated: removed={} carried={} position={} visual={} token={}",r.display.isRemoved(),CorpseCarryService.isCarried(r.display),r.position.equals(r.display.getPos()),r.visual.equals(EvidenceSnapshots.visualOnly(CorpsePoseRecord.data(r.display.getItemStack()))),r.token.equals(Forensics.binding(r.display.getItemStack()).getString("token","")));remove(r.display.getUuid());continue;}
            if(CorpseHistoryController.get(r.display.getUuid())==null){fail(r,"历史载体已改变或丢失");continue;}
            var state=r.session.residentState();
            if(state.phase().equals("FAILED")||state.phase().equals("CLOSED")){fail(r,state.failure());continue;}
            long now=r.world.getTime();if(state.tick()!=r.clock||state.paused()){r.clock=state.tick();r.progressTime=now;}
            if(now-r.progressTime>60){fail(r,"无模拟进展");continue;}
            if(state.terrainRevision()==r.terrainRevision)r.dirty=false;
            if(!state.paused())r.session.advanceClock(state.tick()+3);
            if(!r.moving()&&state.generation()>r.generation){
                // Commit only on this object's scheduled capture tick, within the two-captures budget.
                if(r.sampledTick!=now||r.dirty)continue;
                var pose=state.stable();var old=CorpsePoseRecord.read(CorpsePoseRecord.data(r.display.getItemStack()));
                if(old.isEmpty()||!old.get().parts().equals(pose.poses())){
                    long revision=CorpsePoseRecord.data(r.display.getItemStack()).getLong("corpse_pose_revision",0)+1;
                    CorpsePoseService.applyGenerated(r.display,new CorpsePoseRecord(revision,"generated",r.slim?"slim":"default",pose.poses(),pose.definition()),r.origin);
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
