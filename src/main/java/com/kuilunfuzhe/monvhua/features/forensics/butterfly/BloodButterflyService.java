package com.kuilunfuzhe.monvhua.features.forensics.butterfly;

import com.kuilunfuzhe.monvhua.features.forensics.*;
import com.kuilunfuzhe.monvhua.features.forensics.blood.*;
import com.kuilunfuzhe.monvhua.network.forensics.ButterflyPackets;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import java.util.*;

/** Main-thread, non-entity flights. No chunk tickets or client position authority. */
public final class BloodButterflyService {
    private static final List<Flight> FLIGHTS=new ArrayList<>();
    private static final ArrayDeque<Birth> BIRTHS=new ArrayDeque<>();
    private static final ArrayDeque<Flight> EXPIRED=new ArrayDeque<>();
    private static final Set<UUID> RECIPIENTS=new HashSet<>();
    public static final int PERIOD=100;
    public static final double PLAYER_RADIUS=100,MIN_POOL_MASS=1024;
    private static int sourceCursor,sequence,rotation;
    private record Source(ServerWorld world,String corpse,String event,List<String> patches) {}
    private record Birth(Source source,ArrayDeque<UUID> targets) {}
    private record Attraction(ServerWorld world,UUID target) {}
    private static final class Flight {
        final int id=++sequence,seed,lifetime;final ServerWorld world;final UUID target;
        final double phase;
        Vec3d pos,velocity=Vec3d.ZERO,goal,plannedTarget,progressPoint;
        ButterflyPath search;List<Vec3d> path=List.of();
        int age,stalled,nextPlan,searchAge;boolean arrived,retired,boosted;
        final ArrayDeque<Vec3d> trail=new ArrayDeque<>();
        Flight(ServerWorld w,Vec3d p,UUID target){
            world=w;pos=p;progressPoint=p;this.target=target;seed=w.random.nextInt();phase=(seed&65535)*Math.PI*2/65536;
            lifetime=ButterflyMotion.lifetime(w.random.nextInt(317));
            for(int i=0;i<5;i++)trail.add(p);
        }
    }
    private static final class Budget {
        int queries;final long deadline;
        Budget(){var c=ForensicConfig.current.butterflies;queries=c.searchQueriesPerTick;deadline=System.nanoTime()+(long)(c.searchMillis*1_000_000);}
        boolean clear(ServerWorld w,Vec3d a,Vec3d b){
            return ButterflyClearance.clear(w,a,b,()->{
                if(queries--<=0||System.nanoTime()>=deadline)throw new ButterflyPath.Yield();
            });
        }
    }
    private BloodButterflyService(){}
    /** Server-thread diagnostics, never transmitted to clients. */
    public record Observation(int id,UUID target,Vec3d position,boolean arrived,boolean boosted,int lifetime,int age) {}
    public static List<Observation> observations(ServerWorld world){
        if(!world.getServer().isOnThread())throw new IllegalStateException("Server thread required");
        return FLIGHTS.stream().filter(f->f.world==world).map(f->new Observation(f.id,f.target,f.pos,f.arrived,f.boosted,f.lifetime,f.age)).toList();
    }
    public static List<Vec3d> positions(ServerWorld world){return observations(world).stream().map(Observation::position).toList();}
    private static boolean enabled(){return BloodRules.generating()&&ForensicConfig.current.butterflies.enabled;}
    public static void initialize(){
        ButterflyPackets.register();
        ServerTickEvents.END_SERVER_TICK.register(s->BloodTraceService.safe(()->tick(s)));
        ServerLifecycleEvents.SERVER_STOPPED.register(s->{FLIGHTS.clear();BIRTHS.clear();EXPIRED.clear();RECIPIENTS.clear();sequence=rotation=sourceCursor=0;});
    }
    private static boolean eligible(ServerPlayerEntity p){
        return p.isAlive()&&!p.isSpectator()&&!com.kuilunfuzhe.monvhua.features.forensics.physics.KomDyingCompat.isDying(p);
    }
    public static boolean poolCandidate(BloodTraceStore.Entry entry){
        var p=entry.patch();
        return ButterflyResidue.isBirthSource(entry)&&p.kind()==1&&p.face()==Direction.UP&&p.radius()>=.5f
                &&!entry.source().isEmpty()&&!entry.event().isEmpty();
    }
    /** Only corpse/limb pools qualify; a site spanning several block tiles is one source. */
    private static void cycle(MinecraftServer server){
        BIRTHS.clear();var sources=new ArrayList<Source>();
        for(var world:server.getWorlds()){
            var traces=BloodTraceStore.get(world);var groups=new LinkedHashMap<String,List<String>>();
            for(long key:traces.chunkKeys()){
                var chunk=new ChunkPos(key);
                if(!world.isChunkLoaded(chunk.x,chunk.z)||world.getPlayers().stream().noneMatch(p->eligible(p)
                        &&Math.abs(p.getX()-(chunk.getStartX()+8))<=108&&Math.abs(p.getZ()-(chunk.getStartZ()+8))<=108))continue;
                for(var entry:traces.chunk(key))if(poolCandidate(entry))
                    groups.computeIfAbsent(entry.source()+"/"+entry.event(),k->new ArrayList<>()).add(entry.patch().id());
            }
            for(var ids:groups.values()){
                var entry=traces.entry(ids.getFirst());
                // The persisted source is a forensic corpse record, not the live display UUID.
                if(!ForensicStore.get(server).corpse(entry.source()).isEmpty())
                    sources.add(new Source(world,entry.source(),entry.event(),List.copyOf(ids)));
            }
        }
        if(sources.isEmpty())return;
        for(int i=0;i<Math.min(128,sources.size());i++){
            var source=sources.get(Math.floorMod(sourceCursor+i,sources.size()));
            Vec3d point=sourcePoint(source);if(point==null)continue;
            var targets=new ArrayDeque<UUID>();
            source.world.getPlayers().stream().filter(p->eligible(p)&&p.squaredDistanceTo(point)<=PLAYER_RADIUS*PLAYER_RADIUS)
                    .sorted(Comparator.comparing(p->p.getUuid().toString())).forEach(p->targets.add(p.getUuid()));
            if(!targets.isEmpty())BIRTHS.add(new Birth(source,targets));
        }
        sourceCursor=(sourceCursor+128)%sources.size();
    }
    private static Vec3d sourcePoint(Source source){
        var store=BloodTraceStore.get(source.world);double mass=0,bestMass=0;BloodPatch best=null;
        for(var id:source.patches){
            var entry=store.entry(id);
            if(entry==null||!poolCandidate(entry)||!entry.source().equals(source.corpse)||!entry.event().equals(source.event))continue;
            var patch=entry.patch();
            if(!BloodSurfaceProjection.supported(source.world,patch.pos())
                    ||!source.world.getBlockState(patch.pos()).toString().equals(entry.support()))continue;
            double amount=patch.mass();mass+=amount;
            if(amount>bestMass){bestMass=amount;best=patch;}
        }
        if(mass<MIN_POOL_MASS||best==null)return null;
        // Pick an actually blood-covered location, including partially washed and edge tiles.
        Vec3d origin=best.center().subtract(best.right().multiply(best.u())).subtract(best.down().multiply(best.v()));
        double nearest=Double.MAX_VALUE;Vec3d point=null;
        for(int y=0;y<16;y++)for(int x=0;x<16;x++){
            double u=(x+.5)/16,v=(y+.5)/16;
            if(!best.covers(u,v)||best.density(u,v)<128)continue;
            double d=Math.pow(u-best.u(),2)+Math.pow(v-best.v(),2);
            if(d<nearest){nearest=d;point=origin.add(best.right().multiply(u)).add(best.down().multiply(v)).add(0,.16,0);}
        }
        return point;
    }
    private static void births(){
        var c=ForensicConfig.current.butterflies;
        for(int i=0;i<4&&!BIRTHS.isEmpty();i++){
            var birth=BIRTHS.removeFirst();var source=birth.source;
            UUID id=birth.targets.removeFirst();
            var player=source.world.getServer().getPlayerManager().getPlayer(id);
            Vec3d point=sourcePoint(source);
            if(FLIGHTS.size()<c.maxTotal&&FLIGHTS.stream().filter(f->f.world==source.world).count()<c.maxPerDimension
                    &&point!=null&&player!=null&&player.getWorld()==source.world&&eligible(player)
                    &&player.squaredDistanceTo(point)<=PLAYER_RADIUS*PLAYER_RADIUS&&ButterflyClearance.clear(source.world,point,point))
                FLIGHTS.add(new Flight(source.world,point,id));
            if(!birth.targets.isEmpty())BIRTHS.addLast(birth);
        }
    }
    private static ServerPlayerEntity target(Flight f){
        var p=f.world.getServer().getPlayerManager().getPlayer(f.target);
        return p!=null&&p.getWorld()==f.world&&eligible(p)&&p.squaredDistanceTo(f.pos)<=PLAYER_RADIUS*PLAYER_RADIUS?p:null;
    }
    private static void tick(MinecraftServer server){
        if(!enabled()){FLIGHTS.clear();BIRTHS.clear();EXPIRED.clear();if(server.getTicks()%4==0)send(server);return;}
        if(server.getTicks()%PERIOD==0)cycle(server);
        births();
        var attractions=new HashSet<Attraction>();
        for(var f:FLIGHTS)if(f.arrived&&!f.retired&&f.age+1<f.lifetime){
            var player=target(f);
            if(player!=null&&player.getPos().add(0,1,0).squaredDistanceTo(f.pos)<9)
                attractions.add(new Attraction(f.world,f.target));
        }
        // Movement has its own small, fixed sweep allowance per flight. A* cannot consume it.
        for(var f:FLIGHTS){
            if(++f.age>=f.lifetime){if(EXPIRED.size()<128)EXPIRED.addLast(f);continue;}
            var player=target(f);if(player==null){f.retired=true;continue;}
            f.boosted=!f.arrived&&attractions.contains(new Attraction(f.world,f.target));
            move(f,player);
            f.trail.removeFirst();f.trail.addLast(f.pos);
        }
        var budget=new Budget();int size=FLIGHTS.size();
        for(int i=0;i<size;i++){
            var f=FLIGHTS.get(Math.floorMod(i+rotation,size));
            if(f.retired||f.age>=f.lifetime||f.arrived)continue;
            try{plan(f,budget);}catch(ButterflyPath.Yield pause){/* Movement already ran safely. */}
        }
        rotation++;
        FLIGHTS.removeIf(f->f.retired||f.age>=f.lifetime);
        for(int i=0;i<2&&!EXPIRED.isEmpty();i++){var f=EXPIRED.removeFirst();ButterflyResidue.deposit(f.world,f.pos,f.seed);}
        if(server.getTicks()%4==0)send(server);
    }
    private static void move(Flight f,ServerPlayerEntity player){
        Vec3d center=player.getPos().add(0,1,0);
        if(f.arrived&&f.pos.squaredDistanceTo(center)>9){
            // A moving player can leave the orbit behind; resume the same locked pursuit.
            f.arrived=false;f.goal=null;f.path=List.of();f.search=null;
        }
        if(!f.arrived&&f.pos.squaredDistanceTo(center)<=1.5*1.5){f.arrived=true;f.boosted=false;f.path=List.of();f.search=null;}
        Vec3d destination;
        if(f.arrived){
            destination=ButterflyMotion.orbit(center,f.phase,f.age);
            f.goal=null;
        }else{
            if(f.goal==null||f.pos.squaredDistanceTo(f.goal)<.25||f.plannedTarget.squaredDistanceTo(center)>4){
                Vec3d toward=center.subtract(f.pos);
                f.goal=toward.length()>8?f.pos.add(toward.normalize().multiply(8)):center;
                f.plannedTarget=center;f.search=null;f.searchAge=0;f.path=List.of();
            }
            // Consume reached nodes without spending a stationary tick at every grid point.
            while(!f.path.isEmpty()&&f.pos.squaredDistanceTo(f.path.getFirst())<.12*.12)f.path=f.path.subList(1,f.path.size());
            destination=f.path.isEmpty()?f.goal:f.path.getFirst();
        }
        Vec3d direction=destination.subtract(f.pos);double speed=ButterflyMotion.speed(f.boosted);
        Vec3d curved=ButterflyMotion.steer(f.velocity,direction,f.age,f.phase,speed,f.arrived);
        Vec3d forward=direction.normalize().multiply(speed);
        // Bounded local escape while the global path is still being searched. Every trial is swept.
        double angle=f.phase+f.age*.11;
        Vec3d lateral=new Vec3d(Math.cos(angle),.4*Math.sin(angle*.7),Math.sin(angle)).normalize().multiply(speed);
        Vec3d[] trials={curved,forward,lateral,new Vec3d(0,speed,0),lateral.multiply(-1)};
        for(int i=0;i<trials.length;i++){
            Vec3d delta=trials[i];
            if(delta.lengthSquared()<1e-10)continue;
            Vec3d next=f.pos.add(delta);
            if(ButterflyClearance.clear(f.world,f.pos,next)){
                // Local avoidance must not restart the ongoing global search every tick.
                if(i>=2)f.path=List.of();
                f.velocity=delta;f.pos=next;f.stalled=0;
                if(!f.arrived&&f.age%50==0){
                    if(f.pos.squaredDistanceTo(f.progressPoint)<.3*.3)f.retired=true;
                    f.progressPoint=f.pos;
                }
                return;
            }
        }
        if(++f.stalled>=50)f.retired=true;
        f.velocity=Vec3d.ZERO;f.path=List.of();
    }
    private static void plan(Flight f,Budget budget){
        int now=f.world.getServer().getTicks();
        // Shorten only periodically; failed long sweeps cannot stall the movement pass.
        if(!f.path.isEmpty()){
            if(f.age%4==0)for(int i=Math.min(4,f.path.size()-1);i>0;i--)
                if(budget.clear(f.world,f.pos,f.path.get(i))){f.path=f.path.subList(i,f.path.size());break;}
            return;
        }
        if(f.goal==null||now<f.nextPlan)return;
        if(f.search==null){
            if(f.pos.squaredDistanceTo(f.goal)<=64&&budget.clear(f.world,f.pos,f.goal)){f.path=List.of(f.goal);return;}
            f.search=new ButterflyPath(f.pos,f.goal);f.searchAge=0;
        }
        f.search.step((a,b)->budget.clear(f.world,a,b),12);
        if(f.search.done()){
            var result=f.search.result();f.search=null;
            // Reconnect to the nearest validated route point after local search-time motion.
            if(!result.isEmpty()){
                int closest=0;
                for(int i=1;i<result.size();i++)if(result.get(i).squaredDistanceTo(f.pos)<result.get(closest).squaredDistanceTo(f.pos))closest=i;
                f.path=result.subList(Math.min(closest+1,result.size()-1),result.size());
            }else f.nextPlan=now+4;
        }else if(++f.searchAge>=50){f.search=null;f.nextPlan=now+1;}
    }
    private static void send(MinecraftServer server){
        RECIPIENTS.removeIf(id->server.getPlayerManager().getPlayer(id)==null);
        for(var player:server.getPlayerManager().getPlayerList()){
            if(!ServerPlayNetworking.canSend(player,ButterflyPackets.Sync.ID))continue;
            var flights=FLIGHTS.stream().filter(f->f.world==player.getWorld()&&player.squaredDistanceTo(f.pos)<=48*48)
                .map(f->new ButterflyPackets.Flight(f.id,f.seed,Math.max(0,f.lifetime-f.age),List.copyOf(f.trail))).toList();
            if(flights.isEmpty()){if(!RECIPIENTS.remove(player.getUuid()))continue;}
            else RECIPIENTS.add(player.getUuid());
            ServerPlayNetworking.send(player,new ButterflyPackets.Sync(player.getWorld().getRegistryKey().getValue().toString(),server.getTicks(),
                ForensicConfig.current.butterflies.randomRadius,flights));
        }
    }
}
