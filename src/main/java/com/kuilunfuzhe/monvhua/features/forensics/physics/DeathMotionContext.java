package com.kuilunfuzhe.monvhua.features.forensics.physics;

import com.kuilunfuzhe.monvhua.features.forensics.Forensics;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import java.util.*;

/** Damage scopes exist only on the calling server thread and are removed in finally.
 * Frozen scalar motion is bound to a corpse ID, never searched by last attacker/tick. */
public final class DeathMotionContext {
    private static final ThreadLocal<ArrayDeque<Scope>> ACTIVE=new ThreadLocal<>();
    private record Bound(MinecraftServer server,UUID player,String corpse,int tick,CorpseInitialMotionResolver.Motion motion) {}
    private static final Map<String,Bound> FROZEN=new HashMap<>();
    public static final class Scope implements AutoCloseable {
        private final ServerPlayerEntity player;
        private final DamageSource source;
        private final CorpseInitialMotionResolver.Motion motion;
        private boolean accepted;
        private Scope(ServerPlayerEntity player,DamageSource source){this.player=player;this.source=source;motion=capture(player,source,true);}
        public void close(){var stack=ACTIVE.get();if(stack.peek()!=this)throw new IllegalStateException("Unbalanced damage scope");stack.pop();if(stack.isEmpty())ACTIVE.remove();}
    }
    private DeathMotionContext(){}
    public static Scope begin(ServerPlayerEntity player,DamageSource source) {
        var stack=ACTIVE.get();if(stack==null){stack=new ArrayDeque<>();ACTIVE.set(stack);}
        var scope=new Scope(player,source);stack.push(scope);return scope;
    }
    public static void accepted(ServerPlayerEntity player,DamageSource source) {
        var stack=ACTIVE.get();var s=stack==null?null:stack.peek();if(s!=null&&s.player==player&&s.source==source)s.accepted=true;
    }
    public static synchronized void freeze(ServerPlayerEntity player,DamageSource source,String corpse) {
        var stack=ACTIVE.get();var s=stack==null?null:stack.peek();
        var motion=s!=null&&s.player==player&&s.source==source&&s.accepted?s.motion:capture(player,source,false);
        FROZEN.values().removeIf(v->v.player.equals(player.getUuid())&&v.server==player.getServer());
        FROZEN.put(corpse,new Bound(player.getServer(),player.getUuid(),corpse,player.getServer().getTicks(),motion));
    }
    public static synchronized CorpseInitialMotionResolver.Motion consume(ServerPlayerEntity player,String corpse) {
        var v=FROZEN.remove(corpse);
        if(v!=null&&v.server==player.getServer()&&v.player.equals(player.getUuid())&&v.tick==player.getServer().getTicks())return v.motion;
        return CorpseInitialMotionResolver.resolve(player.bodyYaw,0,0,0,0,"unknown","unknown","missing_death_context",CorpseProfileManager.current());
    }
    public static synchronized void expire(MinecraftServer server) {FROZEN.values().removeIf(v->v.server==server&&v.tick<server.getTicks());}
    public static synchronized void clear(MinecraftServer server) {FROZEN.values().removeIf(v->v.server==server);}
    public static int activeDepth(){var stack=ACTIVE.get();return stack==null?0:stack.size();}
    private static CorpseInitialMotionResolver.Motion capture(ServerPlayerEntity p,DamageSource source,boolean beforeDamage) {
        var direct=source.getSource();var attacker=source.getAttacker();
        String cause=source.getTypeRegistryEntry().getKey().map(k->k.getValue().toString()).orElse("unknown");
        boolean melee=Set.of("minecraft:player_attack","minecraft:mob_attack","minecraft:mob_attack_no_aggro").contains(cause)
                &&direct==attacker&&attacker instanceof LivingEntity;
        double dx=0,dz=0;String kind="environment",quality="unknown",fallback="no_direct_impact";
        if(direct instanceof ProjectileEntity projectile
                &&source.isIn(net.minecraft.registry.tag.DamageTypeTags.IS_PROJECTILE)
                &&!source.isIn(net.minecraft.registry.tag.DamageTypeTags.IS_EXPLOSION)
                &&!source.isIn(net.minecraft.registry.tag.DamageTypeTags.IS_FIRE)) {
            var v=projectile.getVelocity();dx=v.x;dz=v.z;kind="projectile";quality="impact_velocity";fallback="";
            if(!Double.isFinite(dx)||!Double.isFinite(dz)||Math.hypot(dx,dz)<1e-5){dx=0;dz=0;quality="unknown";fallback="projectile_horizontal_velocity_unavailable";}
        }else if(melee){dx=p.getX()-attacker.getX();dz=p.getZ()-attacker.getZ();kind="melee";quality="source_position";fallback="";}
        var baseline=beforeDamage?p.getVelocity():net.minecraft.util.math.Vec3d.ZERO;
        if(!beforeDamage&&fallback.isEmpty())fallback="baseline_unavailable";
        return CorpseInitialMotionResolver.resolve(p.bodyYaw,dx,dz,baseline.x,baseline.z,kind,quality,fallback,CorpseProfileManager.current());
    }
}
