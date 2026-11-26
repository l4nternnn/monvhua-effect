package com.kuilunfuzhe.monvhua.features.forensics.butterfly;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.kuilunfuzhe.monvhua.mixin.ButterflyParticleAccessor;
import com.kuilunfuzhe.monvhua.network.forensics.ButterflyPackets;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.Particle;
import net.minecraft.particle.*;
import net.minecraft.util.math.Vec3d;
import java.lang.reflect.Constructor;
import java.util.*;

/** Only particles created here are driven. Existing bleeding butterflies remain completely autonomous. */
public final class BloodButterflyClient {
    private static final Map<Integer,Visual> VISUALS=new HashMap<>();
    private static final Map<Particle,Visual> CONTROLLED=new IdentityHashMap<>();
    private static Object world;
    private static Constructor<?> options;
    private static boolean unavailable,warned;
    private static int clock;
    private static final class Visual {
        final ButterflyPlayback playback;final int seed;
        int lease,remaining,missed;double radius;
        Vec3d anchor,offset=Vec3d.ZERO;Particle particle;
        Visual(ButterflyPackets.Flight f,long tick){seed=f.seed();anchor=f.trail().getFirst();playback=new ButterflyPlayback(tick,anchor);}
    }
    private BloodButterflyClient(){}
    public static void initialize(){
        ClientPlayNetworking.registerGlobalReceiver(ButterflyPackets.Sync.ID,(p,c)->c.client().execute(()->accept(p)));
        ClientTickEvents.END_CLIENT_TICK.register(BloodButterflyClient::tick);
    }
    private static void reset(){
        for(var v:VISUALS.values())if(v.particle!=null)v.particle.markDead();
        VISUALS.clear();CONTROLLED.clear();
    }
    static void accept(ButterflyPackets.Sync packet){
        var client=MinecraftClient.getInstance();
        if(client.world==null||!client.world.getRegistryKey().getValue().toString().equals(packet.dimension()))return;
        if(world!=client.world){reset();world=client.world;}
        var ids=new HashSet<Integer>();
        for(var f:packet.flights()){
            ids.add(f.id());var v=VISUALS.computeIfAbsent(f.id(),id->new Visual(f,packet.tick()));
            v.lease=clock+20;v.remaining=f.remaining();v.radius=packet.randomRadius();
            v.playback.accept(packet.tick(),f.trail());
        }
        VISUALS.entrySet().removeIf(e->{if(ids.contains(e.getKey()))return false;retire(e.getValue());return true;});
    }
    private static void retire(Visual v){if(v.particle!=null){CONTROLLED.remove(v.particle);v.particle.markDead();}}
    private static void tick(MinecraftClient client){
        if(client.world!=world){reset();world=client.world;}
        if(client.world==null||client.isPaused())return;
        clock++;
        var iterator=VISUALS.values().iterator();
        while(iterator.hasNext()){
            var v=iterator.next();
            if(clock>v.lease||v.remaining--<=0){retire(v);iterator.remove();continue;}
            v.anchor=v.playback.next();
            if(v.particle!=null&&!v.particle.isAlive()){retire(v);v.particle=null;}
            if(v.particle!=null&&++v.missed>3){
                retire(v);v.particle=null;
                // A missing optional tick hook must not leave an uncontrolled external particle alive.
                unavailable=true;
            }
            if(v.particle==null&&!unavailable){
                v.particle=create(client,v);
                if(v.particle!=null){CONTROLLED.put(v.particle,v);v.missed=0;}
            }
            if(v.particle==null&&clock%2==0&&ButterflyClearance.clear(client.world,v.anchor,v.anchor))
                client.world.addParticleClient(new DustParticleEffect(0xA41428,.5f),v.anchor.x,v.anchor.y,v.anchor.z,0,0,0);
        }
    }
    private static Particle create(MinecraftClient client,Visual v){
        try{
            if(options==null)options=Class.forName("com.example.manosaba_ice_expand.particle.BloodButterflyParticleOptions").getConstructor(float.class,float.class);
            ParticleEffect effect=(ParticleEffect)options.newInstance(1f,(float)Math.clamp(v.remaining/160.0,.01,4));
            var random=new Random(v.seed);double angle=random.nextDouble()*Math.PI*2,speed=.035+random.nextDouble()*.075;
            Particle particle=client.particleManager.addParticle(effect,v.anchor.x,v.anchor.y,v.anchor.z,
                    Math.cos(angle)*speed,.005+random.nextDouble()*.035,Math.sin(angle)*speed);
            if(particle!=null&&!(particle instanceof ControlledButterfly)){particle.markDead();unavailable=true;return null;}
            return particle;
        }catch(ReflectiveOperationException|LinkageError|ClassCastException e){
            unavailable=true;
            if(!warned){warned=true;MonvhuaMod.LOGGER.warn("Blood butterfly visual unavailable; using bounded blood motes",e);}
            return null;
        }
    }
    /** Optional external tick TAIL hook: retain original random motion as bounded, swept visual drift. */
    public static void afterNativeTick(Particle particle){
        Visual v=CONTROLLED.get(particle);if(v==null)return;
        var client=MinecraftClient.getInstance();if(client.world==null)return;
        var a=(ButterflyParticleAccessor)particle;
        Vec3d previous=new Vec3d(a.monvhua$prevX(),a.monvhua$prevY(),a.monvhua$prevZ());
        Vec3d nativeMotion=new Vec3d(a.monvhua$x(),a.monvhua$y(),a.monvhua$z()).subtract(previous);
        Vec3d drift=ButterflyPath.drift(v.offset,nativeMotion,v.radius);
        Vec3d desired=v.anchor.add(drift);
        if(!ButterflyClearance.clear(client.world,v.anchor,desired)||!ButterflyClearance.clear(client.world,previous,desired)){
            drift=Vec3d.ZERO;desired=v.anchor;
            if(!ButterflyClearance.clear(client.world,previous,desired))desired=previous;
        }
        particle.setPos(desired.x,desired.y,desired.z);v.offset=drift;v.missed=0;
    }
    static boolean controlledVisualWorks(){return VISUALS.values().stream().anyMatch(v->v.particle instanceof ControlledButterfly
        &&v.particle.isAlive()&&v.missed<=1&&v.offset.lengthSquared()>1e-9&&v.offset.length()<=v.radius+1e-6);}
}
