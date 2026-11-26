package com.kuilunfuzhe.monvhua.features.forensics.physics;

import com.kuilunfuzhe.monvhua.features.forensics.*;
import com.kuilunfuzhe.monvhua.features.physics_preview.CorpseScale;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.*;
import net.minecraft.entity.*;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import java.lang.reflect.Method;
import java.util.*;

/** Optional, server-owned living proxies. Never registers a corpse, inventory, history or evidence carrier. */
public final class KomDyingCompat {
    public static final String MARKER="monvhua_downed_proxy", EVIDENCE="kom_downed";
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger("monvhua-kom-dying");
    private static final Map<UUID,Downed> sessions=new HashMap<>();
    // Keep lifecycle tracking even without a physical backend, after fallback, and during movable countdown.
    private static final Map<UUID,ServerPlayerEntity> episodes=new HashMap<>();
    private static Method active,dying,rescue,prepareDeath;
    private static boolean checked,available,creating;
    private static int terrainCursor;
    private static final class Downed {
        final ServerPlayerEntity player; final ServerWorld world; final Vec3d origin; final ItemDisplayEntity display;
        final ItemStack skin; final CorpsePhysicsBridge.Request request; final CorpsePhysicsBridge.Session session;
        final long started; long clock=-1,progress,terrainRevision; boolean published;
        List<CorpsePhysicsBridge.Box> terrain;
        Downed(ServerPlayerEntity p, ItemDisplayEntity d, ItemStack skin, CorpsePhysicsBridge.Request r, CorpsePhysicsBridge.Session s) {
            player=p;world=(ServerWorld)p.getWorld();origin=p.getPos();display=d;this.skin=skin;request=r;session=s;
            started=progress=world.getTime();terrain=r.terrain();
        }
    }
    private KomDyingCompat() {}
    public static boolean supportedVersion() {
        return FabricLoader.getInstance().getModContainer("kom_dying")
                .map(m->m.getMetadata().getVersion().getFriendlyString().equals("1.2.2")).orElse(false);
    }
    private static boolean api() {
        if(checked)return available;checked=true;
        if(!supportedVersion())return false;
        try {
            Class<?> manager=Class.forName("com.example.dying.DyingManager");
            active=manager.getMethod("isPlayerInActiveDyingState",UUID.class);
            dying=manager.getMethod("isPlayerDying",UUID.class);
            prepareDeath=manager.getMethod("prepareDyingPlayerDeath",ServerPlayerEntity.class);
            // Runtime classes keep this boundary valid in both named development and intermediary production.
            rescue=Class.forName("com.example.dying.RescueManager").getMethod("startRescue",ServerPlayerEntity.class,ServerPlayerEntity.class,MinecraftServer.class);
            available=true;LOG.info("Enabled optional kom_dying 1.2.2 living physics proxy compatibility");
        } catch(ReflectiveOperationException|LinkageError e) { LOG.warn("kom_dying API unavailable; retaining original dying behavior",e); }
        return available;
    }
    private static boolean state(Method method,UUID player) {
        try{return Boolean.TRUE.equals(method.invoke(null,player));}
        catch(ReflectiveOperationException|LinkageError e){available=false;LOG.warn("kom_dying state lookup failed; restoring original models",e);return false;}
    }
    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(KomDyingCompat::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{
            server.execute(()->{episodes.remove(handler.player.getUuid(),handler.player);remove(handler.player.getUuid());});
        });
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->{
            var p=handler.player;
            if(api()&&p.isAlive()&&!state(dying,p.getUuid()))recovered(p);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server->reset());
        // A proxy is transient. Remove any saved leftovers after a crash/restart or chunk reload.
        ServerEntityEvents.ENTITY_LOAD.register((entity,world)->{
            if(!creating&&entity.getCommandTags().contains(MARKER))entity.discard();
        });
    }
    public static int count(){return sessions.size();}
    /** Server-thread query shared by optional visual features; does not create a proxy. */
    public static boolean isDying(ServerPlayerEntity player){return sessions.containsKey(player.getUuid())||(api()&&state(dying,player.getUuid()));}
    public static boolean proxy(ItemStack stack){return CorpsePoseRecord.data(stack).getBoolean(MARKER,false);}
    public static void enter(ServerPlayerEntity player) {
        if(!api()||!state(active,player.getUuid())||!Forensics.enabled(player)||!player.getCommandTags().contains("dead_body")||player.getCommandTags().contains("dead_part"))return;
        remove(player.getUuid());
        var store=ForensicStore.get(player.getServer());store.ensureLivingLife(player.getUuid());
        var life=store.life(player.getUuid());DownedEvidence.begin(life,player.getWorld().getTime());store.markDirty();
        episodes.put(player.getUuid(),player);
        var backend=CorpsePhysicsCoordinator.diagnosticProvider(player.getServer());
        if(backend==null||!CorpsePhysicsCoordinator.automaticEnabled()||!CorpseResidentManager.capacity(1)||!CorpseScale.supported(player.getScale())||player.hasVehicle()||player.isTouchingWater()||player.isGliding()
                ||!BodyHitGeometry.normalGravity(com.kuilunfuzhe.monvhua.features.gravity.GravityMagic.getSurfaceGravityDirection(player)))return;
        CorpsePhysicsBridge.Session session=null;ItemDisplayEntity display=null;
        try {
            var world=(ServerWorld)player.getWorld();var origin=player.getPos();
            var motion=DeathMotionContext.current(player);
            boolean slim=CorpsePhysicsCoordinator.slimModel(player,null);
            var request=new CorpsePhysicsBridge.Request(slim,-MathHelper.wrapDegrees(motion.yaw()),CorpsePhysicsBridge.DESTABILIZATION_SPEED,
                    CorpseTerrainSnapshot.capture(world,origin),new CorpsePhysicsBridge.Motion(motion.vx(),motion.vz(),motion.dx(),motion.dz(),motion.tilt(),motion.profile().motion().angularSpeed()),motion.profile(),player.getScale());
            session=backend.openContinuous(request);
            display=EntityType.ITEM_DISPLAY.create(world,SpawnReason.TRIGGERED);
            if(display==null)throw new IllegalStateException("Cannot create downed visual");
            var skin=new ItemStack(Items.NETHERITE_SCRAP);
            skin.set(DataComponentTypes.ITEM_MODEL,Identifier.of("monvhua","combined_body"));
            skin.set(DataComponentTypes.PROFILE,new ProfileComponent(player.getGameProfile()));
            var data=new NbtCompound();data.putBoolean(MARKER,true);data.putString("downed_player",player.getUuid().toString());
            data.putString("arm_model",slim?"slim":"default");data.putFloat("corpse_scale",player.getScale());
            data.put("corpse_wounds",PostmortemWounds.visuals(life));
            skin.set(DataComponentTypes.CUSTOM_DATA,NbtComponent.of(data));
            // Empty until the first frame. Client suppresses the marked static item at all times.
            display.setPosition(origin);display.addCommandTag(MARKER);
            creating=true;
            try{if(!world.spawnEntity(display))throw new IllegalStateException("Downed spawn rejected");}finally{creating=false;}
            sessions.put(player.getUuid(),new Downed(player,display,skin,request,session));
            LOG.info("Created downed proxy victim={} display={} scale={}",player.getUuid(),display.getUuid(),player.getScale());
        } catch(RuntimeException|LinkageError e) {
            if(session!=null)close(session);if(display!=null)display.discard();
            LOG.warn("Downed physics unavailable for {}; using original player pose",player.getUuid(),e);
        }
    }
    public static void countdown(ServerPlayerEntity player){remove(player.getUuid());}
    public static void recovered(ServerPlayerEntity player){
        episodes.remove(player.getUuid());remove(player.getUuid());var store=ForensicStore.get(player.getServer());
        DownedEvidence.rescued(store.life(player.getUuid()));store.markDirty();
    }
    /** Discard synthetic /kill wounds during a downing episode, never a second attack or environmental hit. */
    public static boolean syntheticDeath(ServerPlayerEntity player,net.minecraft.entity.damage.DamageSource source) {
        return api()&&source.getTypeRegistryEntry().getKey().map(k->k.getValue().toString().equals("minecraft:generic_kill")).orElse(false)
                &&DownedEvidence.synthetic(ForensicStore.get(player.getServer()).life(player.getUuid()),"minecraft:generic_kill");
    }
    private static void tick(MinecraftServer server) {
        for(var player:List.copyOf(episodes.values())) {
            if(player.getServer()!=server)continue;
            if(server.getPlayerManager().getPlayer(player.getUuid())!=player){episodes.remove(player.getUuid(),player);remove(player.getUuid());continue;}
            if(!player.isAlive())CorpseDeathFallback.departure(player,"death");
        }
        var all=List.copyOf(sessions.values());
        var sample=new HashSet<Downed>();
        for(int i=0;i<Math.min(2,all.size());i++)sample.add(all.get(Math.floorMod(terrainCursor++,all.size())));
        for(var d:all) {
            try {
                if(d.player.getServer()!=server||server.getPlayerManager().getPlayer(d.player.getUuid())!=d.player||!d.player.isAlive()
                        ||d.player.getWorld()!=d.world||d.display.isRemoved()||d.player.getPos().squaredDistanceTo(d.origin)>9||Math.abs(d.player.getScale()-d.request.scale())>1e-4
                        ||!available||!state(active,d.player.getUuid())) {remove(d.player.getUuid());continue;}
                if(!d.session.ready().isDone()){if(d.world.getTime()-d.started>60)remove(d.player.getUuid());continue;}
                d.session.ready().join();var s=d.session.residentState();
                if(s==null||s.phase().equals("FAILED")||s.phase().equals("CLOSED"))throw new IllegalStateException(s==null?"Missing resident state":s.failure());
                long now=d.world.getTime();if(s.tick()!=d.clock||s.paused()||CorpseIdlePolicy.resting(s)){d.clock=s.tick();d.progress=now;}
                if(now-d.progress>60)throw new IllegalStateException("Downed solver stopped progressing");
                if(sample.contains(d)){
                    var terrain=CorpseTerrainSnapshot.capture(d.world,d.origin);
                    if(!terrain.equals(d.terrain)){d.session.terrain(terrain,++d.terrainRevision);d.terrain=terrain;}
                }
                if(CorpseIdlePolicy.advance(s,false,s.terrainRevision()!=d.terrainRevision)){
                    d.session.advanceClock(s.tick()+3);com.kuilunfuzhe.monvhua.features.forensics.CorpsePerformance.count(com.kuilunfuzhe.monvhua.features.forensics.CorpsePerformance.Counter.PHYSICS_ADVANCED);
                }else if(CorpseIdlePolicy.resting(s))com.kuilunfuzhe.monvhua.features.forensics.CorpsePerformance.count(com.kuilunfuzhe.monvhua.features.forensics.CorpsePerformance.Counter.PHYSICS_IDLE_SKIPPED);
                var frames=d.session.frames();if(frames.isEmpty())continue;
                var root=frames.getLast().poses().get("torso");
                if(root==null||new Vec3d(root.x(),root.y(),root.z()).lengthSquared()>9)throw new IllegalStateException("Downed body left rescue anchor");
                if(!d.published){d.display.setItemStack(d.skin.copy());d.published=true;}
                CorpsePhysicsCoordinator.publish(new CorpsePhysicsCoordinator.LiveView(d.display.getUuid(),d.world.getRegistryKey().getValue().toString(),d.origin,d.skin.copy(),d.request.slim(),d.session,d.player.getUuid(),0));
            } catch(RuntimeException|LinkageError e){LOG.warn("Stopping downed proxy for {}: {}",d.player.getUuid(),e.toString());remove(d.player.getUuid());}
        }
    }
    /** Runs synchronously inside the real AFTER_DEATH carrier creation; no map-disappearance guessing. */
    public static boolean handoff(ServerPlayerEntity player,ItemDisplayEntity display,String part) {
        episodes.remove(player.getUuid());
        var d=sessions.remove(player.getUuid());if(d==null)return false;
        boolean adopted=false;
        try {
            if(d.player!=player||!part.equals("all")||display==null||display.getWorld()!=d.world||d.session.frames().isEmpty())return false;
            adopted=CorpseResidentManager.adopt(display,d.origin,d.request,d.session,player.getUuid(),d.terrainRevision);
            if(adopted){LOG.info("Transferred downed session victim={} corpse={}",player.getUuid(),display.getUuid());return true;}
            LOG.warn("Downed history budget unavailable; retaining the last physical frame for {}",player.getUuid());
            return CorpseResidentManager.commitDownedFrame(display,d.origin,d.request,d.session);
        } catch(RuntimeException|LinkageError e){LOG.warn("Downed handoff failed; using ordinary corpse fall",e);return false;}
        finally {CorpsePhysicsCoordinator.unpublish(d.display.getUuid());d.display.discard();if(!adopted)close(d.session);}
    }
    public static void remove(UUID player) {
        var d=sessions.remove(player);if(d==null)return;
        CorpsePhysicsCoordinator.unpublish(d.display.getUuid());d.display.discard();close(d.session);
    }
    private static void close(CorpsePhysicsBridge.Session session){try{session.close();}catch(RuntimeException e){LOG.warn("Downed session close failed",e);}}
    /** Run only after the official corpse has been committed, without reporting a successful rescue. */
    public static void finishTerminal(ServerPlayerEntity player){
        episodes.remove(player.getUuid());remove(player.getUuid());
        if(!api())return;
        try{prepareDeath.invoke(null,player);}
        catch(ReflectiveOperationException|LinkageError e){LOG.warn("Could not clear terminal kom_dying state for {}",player.getUuid(),e);}
    }
    public static void clearProxies(){for(var id:List.copyOf(sessions.keySet()))remove(id);}
    public static void reset(){episodes.clear();clearProxies();}
    /** Uses the authority ray, clipped by world blocks. The client cannot name a remote victim. */
    public static void rescue(ServerPlayerEntity rescuer) {
        if(!api()||!rescuer.isAlive()||rescuer.isSpectator()||!rescuer.getMainHandStack().isEmpty())return;
        Downed nearest=null;double distance=Double.POSITIVE_INFINITY;
        for(var d:sessions.values()) {
            if(d.player==rescuer||d.world!=rescuer.getWorld()||!d.published||rescuer.squaredDistanceTo(d.player)>16)continue;
            var view=new CorpsePhysicsCoordinator.LiveView(d.display.getUuid(),d.world.getRegistryKey().getValue().toString(),d.origin,d.skin,d.request.slim(),d.session,d.player.getUuid(),0);
            // Temporary ray adapter only: never registered in the corpse/history maps.
            double hit=CorpseHistoryPicking.hit(rescuer,new CorpseHistoryController.Entry(d.display,view,0));
            if(hit>=0&&hit<distance){nearest=d;distance=hit;}
        }
        if(nearest==null||!state(active,nearest.player.getUuid()))return;
        try {rescue.invoke(null,rescuer,nearest.player,rescuer.getServer());}
        catch(ReflectiveOperationException|LinkageError e){LOG.warn("Could not forward proxy rescue",e);}
    }
}
