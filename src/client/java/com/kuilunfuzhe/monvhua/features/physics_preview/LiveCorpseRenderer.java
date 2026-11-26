package com.kuilunfuzhe.monvhua.features.physics_preview;

import com.kuilunfuzhe.monvhua.features.forensics.CorpsePoseRecord;
import com.kuilunfuzhe.monvhua.features.forensics.physics.CorpsePhysicsCoordinator;
import com.kuilunfuzhe.monvhua.features.forensics.physics.CorpseHistoryController;
import com.kuilunfuzhe.monvhua.features.forensics.physics.CorpsePhysicsBridge;
import com.kuilunfuzhe.monvhua.renderer.body.special.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import java.util.*;

/** In-process singleplayer view. Live transforms never enter the persisted SETTLED protocol. */
public final class LiveCorpseRenderer {
    private static CombinedBodySpecialModelRenderer renderer;
    private static Object models;
    private record Playback(CorpsePhysicsBridge.Session session,PhysicsPlaybackClock clock) {}
    private static final Map<UUID,Playback> playback=new HashMap<>();
    private record Bounds(List<CorpsePhysicsBridge.TimedPose> frames,boolean slim,net.minecraft.util.math.Box box) {}
    private static final Map<UUID,Bounds> bounds=new HashMap<>();
    public record DiagnosticSample(long nanos,double displayed,long latest,String mode,long waits,long resyncs){}
    private static final boolean DIAGNOSTICS=Boolean.getBoolean("monvhua.physicsPlaybackDiagnostics")||Boolean.getBoolean("monvhua.physicsG4GameTest")||Boolean.getBoolean("monvhua.physicsAutomaticGameTest")||Boolean.getBoolean("monvhua.physicsHistoryGameTest");
    private static final LinkedHashMap<UUID,ArrayDeque<DiagnosticSample>> diagnostics=new LinkedHashMap<>();
    public static List<DiagnosticSample> diagnosticSamples(UUID id){var samples=diagnostics.get(id);return samples==null?List.of():List.copyOf(samples);}
    public static void initialize() {
        ClientTickEvents.START_CLIENT_TICK.register(client->{
            if(client.world==null){playback.clear();bounds.clear();diagnostics.clear();renderer=null;models=null;return;}
            if(client.isPaused())return;
            var views=RemoteCorpsePhysics.views();
            var ids=new HashSet<UUID>();
            for(var view:views) {
                ids.add(view.display());
                if(!sameWorld(view)){if(view.committedRevision()>0)RemoteCorpsePhysics.acknowledge(view.display(),view.committedRevision());continue;}
                if(view.committedRevision()>0) {
                    ItemDisplayEntity display=null;
                    for(var e:client.world.getEntities())if(e.getUuid().equals(view.display())&&e instanceof ItemDisplayEntity d){display=d;break;}
                    if(display!=null&&CorpsePoseRecord.read(CorpsePoseRecord.data(display.getItemStack())).map(p->p.revision()==view.committedRevision()).orElse(false))
                        RemoteCorpsePhysics.acknowledge(view.display(),view.committedRevision());

                }
            }
            for(var e:CorpseHistoryController.entries())if(e.frozen())ids.add(e.id);
            playback.keySet().retainAll(ids);
            bounds.keySet().retainAll(ids);
        });
        WorldRenderEvents.AFTER_ENTITIES.register(LiveCorpseRenderer::render);
    }
    private static boolean sameWorld(CorpsePhysicsCoordinator.LiveView v) {
        var client=MinecraftClient.getInstance();
        return client.world!=null&&client.world.getRegistryKey().getValue().toString().equals(v.dimension());
    }
    public static boolean hides(Entity entity) {
        if(!(entity instanceof ItemDisplayEntity)&&!(entity instanceof LivingEntity))return false;
        // Proxies are rendered only through validated live frames, never as upright static items.
        if(entity.getCommandTags().contains(com.kuilunfuzhe.monvhua.features.forensics.physics.KomDyingCompat.MARKER)
                ||entity instanceof ItemDisplayEntity d&&com.kuilunfuzhe.monvhua.features.forensics.physics.KomDyingCompat.proxy(d.getItemStack()))return true;
        var history=CorpseHistoryController.get(entity.getUuid());
        if(history!=null&&history.valid&&history.frozen()&&history.view!=null&&sameWorld(history.view))return true;
        for(var v:RemoteCorpsePhysics.views())if(sameWorld(v)) {
            if(entity.getUuid().equals(v.victim())&&com.kuilunfuzhe.monvhua.features.forensics.physics.KomDyingCompat.proxy(v.skin())&&!v.session().frames().isEmpty())return true;
            if(entity.getUuid().equals(v.display()))return true;
            if(entity instanceof LivingEntity living&&!living.isAlive()&&entity.getUuid().equals(v.victim()))return true;
        }
        return false;
    }
    static BodyPartSpecialModelRenderer.Data renderData(CombinedBodySpecialModelRenderer renderer,net.minecraft.item.ItemStack stack,boolean slim){
        var data=renderer.getData(stack);String shape=slim?"slim":"default";
        if(data!=null)return new BodyPartSpecialModelRenderer.Data(data.layer(),data.texture(),shape,data.customData());
        // Profile/skin resolution may still be pending on the first live frame.
        var texture=net.minecraft.util.Identifier.ofVanilla("textures/entity/player/"+(slim?"slim/alex.png":"wide/steve.png"));
        return new BodyPartSpecialModelRenderer.Data(net.minecraft.client.render.RenderLayer.getEntityCutoutNoCull(texture),texture,shape,CorpsePoseRecord.data(stack));
    }
    private static void render(WorldRenderContext context) {
        if(context.matrixStack()==null||context.consumers()==null)return;
        var client=MinecraftClient.getInstance();
        var views=new ArrayList<>(RemoteCorpsePhysics.views());
        for(var e:CorpseHistoryController.entries())if(e.valid&&e.frozen()&&e.view!=null&&views.stream().noneMatch(v->v.display().equals(e.id)))views.add(e.view);
        if(views.isEmpty())return;
        if(models!=client.getLoadedEntityModels()){models=client.getLoadedEntityModels();renderer=new CombinedBodySpecialModelRenderer(client.getLoadedEntityModels());}
        for(var v:views) {
            if(!sameWorld(v))continue;
            var timed=v.session().published();
            var frames=timed.stream().map(CorpsePhysicsBridge.TimedPose::pose).toList();
            if(frames.isEmpty())continue;
            var history=CorpseHistoryController.get(v.display());
            // Frozen history may select a frame outside the recent window. Leave that path uncullled.
            if(history==null||!history.frozen()){
                var cached=bounds.get(v.display());
                if(cached==null||cached.slim()!=v.slim()||!cached.frames().equals(timed)){
                    cached=new Bounds(List.copyOf(timed),v.slim(),CorpseRenderBounds.of(timed,v.slim()));bounds.put(v.display(),cached);
                }
                if(cached.box()!=null&&context.frustum()!=null&&!context.frustum().isVisible(cached.box().offset(v.origin())))continue;
            }
            var last=frames.getLast();
            // Settled bodies are stationary for 30 frames. Hold their exact terminal frame
            // until the tracked static pose arrives on the client.
            if(v.committedRevision()>0){var terminal=v.session().frames();if(!terminal.isEmpty())last=terminal.getLast();}
            var pose=last;
            long latestTick=timed.getLast().tick();
            double displayed=latestTick;String mode=v.committedRevision()>0?"TERMINAL":"RUN";
            boolean globalPause=client.isPaused()||client.world.getTickManager().isFrozen();
            var clock=playback.compute(v.display(),(id,old)->old!=null&&old.session()==v.session()?old:new Playback(v.session(),new PhysicsPlaybackClock())).clock();
            long now=System.nanoTime();
            if(v.session().resting()) { clock.holdAt(latestTick,now);mode="RESTING"; }
            if(v.committedRevision()==0&&!v.session().paused()&&!v.session().resting()&&frames.size()>1) {
                double time=clock.advance(timed.getFirst().tick(),latestTick,now,globalPause);
                displayed=time;if(globalPause)mode="GLOBAL_PAUSE";
                PhysicsPoseDocument a=frames.getFirst(),b=last;
                long at=timed.getFirst().tick(),bt=latestTick;
                for(var f:timed){if(f.tick()<=time){a=f.pose();at=f.tick();}if(f.tick()>=time){b=f.pose();bt=f.tick();break;}}
                float alpha=at==bt?0:(float)Math.clamp((time-at)/(bt-at),0,1);
                var parts=new LinkedHashMap<String,PhysicsPoseDocument.Pose>();
                for(String id:last.poses().keySet()){var x=a.poses().get(id);var y=b.poses().get(id);var q=x.rotation().slerp(y.rotation(),alpha);
                    parts.put(id,new PhysicsPoseDocument.Pose(x.x()+(y.x()-x.x())*alpha,x.y()+(y.y()-x.y())*alpha,x.z()+(y.z()-x.z())*alpha,q.x,q.y,q.z,q.w));}
                pose=new PhysicsPoseDocument("SIMULATING",last.step(),parts,0,"interpolated",last.definition(),last.scale());
            }
            if(history!=null&&history.frozen()){
                var frozen=history.pose();if(frozen!=null){pose=frozen;clock.holdAt(latestTick,now);displayed=history.selectedFrame<0?history.last():history.selectedFrame;mode="FROZEN";}
            }
            if(DIAGNOSTICS){
                var samples=diagnostics.computeIfAbsent(v.display(),id->new ArrayDeque<>());
                samples.addLast(new DiagnosticSample(now,displayed,latestTick,mode,clock.waits(),clock.resyncs()));
                if(samples.size()>2048)samples.removeFirst();
                while(diagnostics.size()>16)diagnostics.remove(diagnostics.keySet().iterator().next());
            }
            var data=renderData(renderer,v.skin(),v.slim());
            var stack=context.matrixStack();var camera=context.camera().getPos();stack.push();
            try {stack.translate(v.origin().x-camera.x,v.origin().y-camera.y,v.origin().z-camera.z);
                renderer.renderPhysicsPreview(stack,context.consumers(),data,StandardBodyPhysicsAdapter.matrices(pose,v.slim()));
            }finally{stack.pop();}
        }
    }
}
