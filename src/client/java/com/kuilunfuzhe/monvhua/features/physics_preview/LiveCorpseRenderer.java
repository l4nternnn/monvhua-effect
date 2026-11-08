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
    public record DiagnosticSample(long nanos,double displayed,int latest,String mode,long waits,long resyncs){}
    private static final boolean DIAGNOSTICS=Boolean.getBoolean("monvhua.physicsPlaybackDiagnostics")||Boolean.getBoolean("monvhua.physicsAutomaticGameTest")||Boolean.getBoolean("monvhua.physicsHistoryGameTest");
    private static final LinkedHashMap<UUID,ArrayDeque<DiagnosticSample>> diagnostics=new LinkedHashMap<>();
    public static List<DiagnosticSample> diagnosticSamples(UUID id){var samples=diagnostics.get(id);return samples==null?List.of():List.copyOf(samples);}
    public static void initialize() {
        ClientTickEvents.START_CLIENT_TICK.register(client->{
            if(client.world==null){playback.clear();diagnostics.clear();renderer=null;models=null;return;}
            if(client.isPaused())return;
            var views=CorpsePhysicsCoordinator.liveViews();
            var ids=new HashSet<UUID>();
            for(var view:views) {
                ids.add(view.display());
                if(!sameWorld(view)){if(view.committedRevision()>0)CorpsePhysicsCoordinator.acknowledge(view.display(),view.committedRevision());continue;}
                if(view.committedRevision()>0) {
                    ItemDisplayEntity display=null;
                    for(var e:client.world.getEntities())if(e.getUuid().equals(view.display())&&e instanceof ItemDisplayEntity d){display=d;break;}
                    if(display!=null&&CorpsePoseRecord.read(CorpsePoseRecord.data(display.getItemStack())).map(p->p.revision()==view.committedRevision()).orElse(false))
                        CorpsePhysicsCoordinator.acknowledge(view.display(),view.committedRevision());

                }
            }
            for(var e:CorpseHistoryController.entries())if(e.frozen())ids.add(e.id);
            playback.keySet().retainAll(ids);
        });
        WorldRenderEvents.AFTER_ENTITIES.register(LiveCorpseRenderer::render);
    }
    private static boolean sameWorld(CorpsePhysicsCoordinator.LiveView v) {
        var client=MinecraftClient.getInstance();
        return client.world!=null&&client.getServer()!=null&&client.world.getRegistryKey().getValue().toString().equals(v.dimension());
    }
    public static boolean hides(Entity entity) {
        var history=CorpseHistoryController.get(entity.getUuid());
        if(history!=null&&history.valid&&history.frozen()&&history.view!=null&&sameWorld(history.view))return true;
        for(var v:CorpsePhysicsCoordinator.liveViews())if(sameWorld(v)) {
            if(entity.getUuid().equals(v.display()))return true;
            if(entity instanceof LivingEntity living&&!living.isAlive()&&entity.getUuid().equals(v.victim()))return true;
        }
        return false;
    }
    private static void render(WorldRenderContext context) {
        if(context.matrixStack()==null||context.consumers()==null)return;
        var client=MinecraftClient.getInstance();
        var views=new ArrayList<>(CorpsePhysicsCoordinator.liveViews());
        for(var e:CorpseHistoryController.entries())if(e.valid&&e.frozen()&&e.view!=null&&views.stream().noneMatch(v->v.display().equals(e.id)))views.add(e.view);
        if(views.isEmpty())return;
        if(models!=client.getLoadedEntityModels()){models=client.getLoadedEntityModels();renderer=new CombinedBodySpecialModelRenderer(client.getLoadedEntityModels());}
        for(var v:views) {
            if(!sameWorld(v)||context.camera().getPos().squaredDistanceTo(v.origin())>64*64)continue;
            var frames=v.session().frames();
            if(frames.isEmpty())continue;
            var last=frames.getLast();
            // Settled bodies are stationary for 30 frames. Hold their exact terminal frame
            // until the tracked static pose arrives on the client.
            if(v.committedRevision()>0){var terminal=v.session().frames();if(!terminal.isEmpty())last=terminal.getLast();}
            var pose=last;
            double displayed=last.step();String mode=v.committedRevision()>0?"TERMINAL":"RUN";
            boolean globalPause=client.isPaused()||client.world.getTickManager().isFrozen();
            var clock=playback.compute(v.display(),(id,old)->old!=null&&old.session()==v.session()?old:new Playback(v.session(),new PhysicsPlaybackClock())).clock();
            long now=System.nanoTime();
            if(v.committedRevision()==0&&frames.size()>1) {
                float time=(float)clock.advance(frames.getFirst().step(),last.step(),now,globalPause);
                displayed=time;if(globalPause)mode="GLOBAL_PAUSE";
                PhysicsPoseDocument a=frames.getFirst(),b=last;
                for(var f:frames){if(f.step()<=time)a=f;if(f.step()>=time){b=f;break;}}
                float alpha=a.step()==b.step()?0:Math.clamp((time-a.step())/(b.step()-a.step()),0,1);
                var parts=new LinkedHashMap<String,PhysicsPoseDocument.Pose>();
                for(String id:PhysicsPoseDocument.IDS){var x=a.poses().get(id);var y=b.poses().get(id);var q=x.rotation().slerp(y.rotation(),alpha);
                    parts.put(id,new PhysicsPoseDocument.Pose(x.x()+(y.x()-x.x())*alpha,x.y()+(y.y()-x.y())*alpha,x.z()+(y.z()-x.z())*alpha,q.x,q.y,q.z,q.w));}
                pose=new PhysicsPoseDocument("SIMULATING",last.step(),parts,0,"interpolated",last.definition());
            }
            var history=CorpseHistoryController.get(v.display());
            if(history!=null&&history.frozen()){
                var frozen=history.pose();if(frozen!=null){pose=frozen;clock.holdAt(last.step(),now);displayed=frozen.step();mode="FROZEN";}
            }
            if(DIAGNOSTICS){
                var samples=diagnostics.computeIfAbsent(v.display(),id->new ArrayDeque<>());
                samples.addLast(new DiagnosticSample(now,displayed,last.step(),mode,clock.waits(),clock.resyncs()));
                if(samples.size()>2048)samples.removeFirst();
                while(diagnostics.size()>16)diagnostics.remove(diagnostics.keySet().iterator().next());
            }
            var data=renderer.getData(v.skin());
            data=new BodyPartSpecialModelRenderer.Data(data.layer(),data.texture(),v.slim()?"slim":"default",data.customData());
            var stack=context.matrixStack();var camera=context.camera().getPos();stack.push();
            try {stack.translate(v.origin().x-camera.x,v.origin().y-camera.y,v.origin().z-camera.z);
                renderer.renderPhysicsPreview(stack,context.consumers(),data,StandardBodyPhysicsAdapter.matrices(pose,v.slim()));
            }finally{stack.pop();}
        }
    }
}
