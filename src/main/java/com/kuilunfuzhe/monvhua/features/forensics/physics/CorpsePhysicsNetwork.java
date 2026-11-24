package com.kuilunfuzhe.monvhua.features.forensics.physics;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.nbt.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Text;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import com.kuilunfuzhe.monvhua.features.forensics.CorpsePerformance;
import java.util.*;

/** Server-authoritative snapshots and bounded grab inputs. Never accepts poses from clients. */
public final class CorpsePhysicsNetwork {
    public record Snapshot(NbtCompound data) implements CustomPayload {
        public static final Id<Snapshot> ID=new Id<>(Identifier.of("monvhua","corpse_live_snapshot"));
        public static final PacketCodec<RegistryByteBuf,Snapshot> CODEC=PacketCodec.of((v,b)->b.writeNbt(v.data),b->new Snapshot(Objects.requireNonNull(b.readNbt())));
        public Id<? extends CustomPayload> getId(){return ID;}
    }
    public record Input(String action,boolean partial,double wheel,double x,double y,double z,boolean rotating) implements CustomPayload {
        public static final Id<Input> ID=new Id<>(Identifier.of("monvhua","corpse_live_input"));
        public static final PacketCodec<RegistryByteBuf,Input> CODEC=PacketCodec.of((v,b)->{b.writeString(v.action,16);b.writeBoolean(v.partial);b.writeDouble(v.wheel);b.writeDouble(v.x);b.writeDouble(v.y);b.writeDouble(v.z);b.writeBoolean(v.rotating);},b->new Input(b.readString(16),b.readBoolean(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readBoolean()));
        public Id<? extends CustomPayload> getId(){return ID;}
    }
    public record Timeline(String action,String target,long frame) implements CustomPayload {
        public static final Id<Timeline> ID=new Id<>(Identifier.of("monvhua","corpse_timeline"));
        public static final PacketCodec<RegistryByteBuf,Timeline> CODEC=PacketCodec.of((p,b)->{b.writeString(p.action,16);b.writeString(p.target,36);b.writeLong(p.frame);},b->new Timeline(b.readString(16),b.readString(36),b.readLong()));
        public Id<? extends CustomPayload> getId(){return ID;}
    }
    private static final Map<UUID,Integer> lastInput=new HashMap<>(),lastAction=new HashMap<>();
    private static final Map<ServerPlayerEntity,CorpseSnapshotGate> snapshots=new HashMap<>();
    private record Prepared(String dimension,Vec3d anchor,Set<ServerPlayerEntity> observers,NbtCompound data) {}
    public static boolean validInput(double wheel,double x,double y,double z){return Double.isFinite(wheel)&&Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z)&&Math.abs(wheel)<=8&&x*x+y*y+z*z<=4.000001;}
    public static NbtCompound encodeGrabStatus(CorpseGrabController.Status status){
        var n=new NbtCompound();n.putString("id",status.id().toString());n.putString("part",status.part());n.putBoolean("local",status.local());n.putBoolean("frozen",status.frozen());n.putDouble("distance",status.distance());
        n.put("actual",point(status.actual()));n.put("target",point(status.target()));return n;
    }
    public static CorpseGrabController.Status decodeGrabStatus(NbtCompound n){
        if(n.isEmpty())return null;
        try{return new CorpseGrabController.Status(UUID.fromString(n.getString("id","")),n.getString("part","torso"),n.getBoolean("local",false),n.getBoolean("frozen",false),n.getDouble("distance",0),point(n.getCompoundOrEmpty("actual")),point(n.getCompoundOrEmpty("target")),"");}
        catch(IllegalArgumentException error){return null;}
    }
    private static NbtCompound point(Vec3d p){var n=new NbtCompound();n.putDouble("x",p.x);n.putDouble("y",p.y);n.putDouble("z",p.z);return n;}
    private static Vec3d point(NbtCompound n){
        double x=n.getDouble("x",Double.NaN),y=n.getDouble("y",Double.NaN),z=n.getDouble("z",Double.NaN);
        if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))throw new IllegalArgumentException("Invalid grab marker");
        return new Vec3d(x,y,z);
    }
    public static void initialize(){
        PayloadTypeRegistry.playS2C().register(Snapshot.ID,Snapshot.CODEC);PayloadTypeRegistry.playC2S().register(Input.ID,Input.CODEC);PayloadTypeRegistry.playC2S().register(Timeline.ID,Timeline.CODEC);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server->{lastInput.clear();lastAction.clear();snapshots.clear();});
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->server.execute(()->{
            snapshots.remove(handler.player);
            var id=handler.player.getUuid();var current=server.getPlayerManager().getPlayer(id);
            if(current!=null&&current!=handler.player)return; // A delayed old disconnect cannot clear a new connection's controls.
            CorpseGrabController.release(id);CorpseTransportController.release(id);CorpseHistoryController.disconnect(id);lastInput.remove(id);lastAction.remove(id);
        }));
        ServerPlayNetworking.registerGlobalReceiver(Input.ID,(packet,context)->context.server().execute(()->{
            var p=context.player();if(!p.isAlive()||p.isSpectator())return;
            var limits=packet.action.equals("input")?lastInput:lastAction;int now=context.server().getTicks();
            if(!packet.action.equals("release")&&limits.getOrDefault(p.getUuid(),-100)==now)return;limits.put(p.getUuid(),now);
            if(!validInput(packet.wheel,packet.x,packet.y,packet.z))return;
            try{switch(packet.action){
                case "rescue"->KomDyingCompat.rescue(p);
                case "toggle"->CorpseGrabController.toggle(p,packet.partial);
                case "freeze"->CorpseGrabController.freeze(p,packet.partial);
                case "range"->CorpseGrabController.range(p);
                case "static"->CorpseGrabController.staticPress(p);
                case "release"->CorpseGrabController.release(p.getUuid());
                case "input"->CorpseGrabController.input(p,packet.wheel,new Vec3d(packet.x,packet.y,packet.z),packet.rotating);
                default->{}
            }}catch(RuntimeException error){p.sendMessage(Text.literal(error.getMessage()==null?"物理操作失败":error.getMessage()),true);}
        }));
        ServerPlayNetworking.registerGlobalReceiver(Timeline.ID,(packet,context)->context.server().execute(()->{
            var p=context.player();if(!p.isAlive()||p.isSpectator())return;
            int now=context.server().getTicks();if(!packet.action.equals("close")&&lastAction.getOrDefault(p.getUuid(),-100)==now)return;lastAction.put(p.getUuid(),now);
            try{
                UUID target=packet.target.isEmpty()?null:UUID.fromString(packet.target);
                switch(packet.action){
                    case "open","freeze" -> CorpseHistoryController.operate(p,target,packet.action.equals("open"));
                    case "select" -> CorpseHistoryController.selectTimeline(p,target,packet.frame);
                    case "close" -> {if(target!=null)CorpseHistoryController.closeTimeline(target,p.getUuid());}
                    default -> {}
                }
            }catch(IllegalArgumentException error){p.sendMessage(Text.literal(error.getMessage()),true);}
        }));
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(server.getTicks()%2!=0)return;
            long performanceStarted=com.kuilunfuzhe.monvhua.features.forensics.CorpsePerformance.begin();
            try {
            var players=server.getPlayerManager().getPlayerList().stream().filter(p->ServerPlayNetworking.canSend(p,Snapshot.ID)).toList();
            snapshots.keySet().retainAll(players); // Respawn replaces the player entity without a disconnect event.
            if(players.isEmpty())return;
            var views=prepare(server);
            for(var player:players){
                var root=new NbtCompound();var list=new NbtList();root.putString("dimension",player.getWorld().getRegistryKey().getValue().toString());
                for(var view:views){
                    if(list.size()>=24||!view.dimension().equals(root.getString("dimension",""))||!view.observers().contains(player)
                            ||player.getPos().squaredDistanceTo(view.anchor())>64*64)continue;
                    list.add(view.data()); // Read-only shared snapshot; no observer-specific fields are stored here.
                }root.put("views",list);
                var status=CorpseGrabController.status(player.getUuid());if(status!=null)root.put("grab",encodeGrabStatus(status));
                var timeline=CorpseHistoryController.timeline(player.getUuid());if(timeline!=null){var n=new NbtCompound();n.putString("id",timeline.id.toString());n.putBoolean("confirmed",timeline.confirmed());n.putBoolean("manual",timeline.manual);n.putBoolean("active",timeline.active);n.putLong("selected",timeline.selectedFrame);n.putLongArray("steps",timeline.steps().stream().limit(721).mapToLong(Long::longValue).toArray());root.put("timeline",n);}
                var gate=snapshots.computeIfAbsent(player,p->new CorpseSnapshotGate());long now=System.nanoTime();
                if(gate.shouldSend(root,now)){
                    ServerPlayNetworking.send(player,new Snapshot(root));gate.sent(root,now);
                    CorpsePerformance.count(CorpsePerformance.Counter.NETWORK_PACKET_SENT);
                }else CorpsePerformance.count(CorpsePerformance.Counter.NETWORK_PACKET_SKIPPED);
            }
            } finally { com.kuilunfuzhe.monvhua.features.forensics.CorpsePerformance.end(com.kuilunfuzhe.monvhua.features.forensics.CorpsePerformance.Metric.NETWORK,performanceStarted); }
        });
    }
    /** Capture each visible session once per broadcast, never once per observer. */
    private static List<Prepared> prepare(MinecraftServer server){
        var views=new LinkedHashMap<UUID,CorpsePhysicsCoordinator.LiveView>();
        for(var view:CorpsePhysicsCoordinator.liveViews())views.put(view.display(),view);
        for(var entry:CorpseHistoryController.entries())if(entry.valid&&entry.frozen()&&entry.view!=null)views.putIfAbsent(entry.id,entry.view);
        var worlds=new HashMap<String,ServerWorld>();for(var world:server.getWorlds())worlds.put(world.getRegistryKey().getValue().toString(),world);
        var result=new ArrayList<Prepared>();
        for(var view:views.values()){
            var world=worlds.get(view.dimension());if(world==null)continue;
            var entity=world.getEntity(view.display());if(entity==null||entity.isRemoved())continue;
            var observers=Set.copyOf(PlayerLookup.tracking(entity));if(observers.isEmpty())continue;
            var frames=view.session().published();if(frames.isEmpty())continue;
            var latest=frames.getLast();var pose=latest.pose();long poseTick=latest.tick();
            var history=CorpseHistoryController.get(view.display());boolean frozen=history!=null&&history.frozen();
            if(frozen){var selected=history.pose();if(selected!=null){pose=selected;poseTick=history.selectedFrame<0?history.last():history.nearest(history.selectedFrame);}}
            var torso=pose.poses().get(pose.rootPart());if(torso==null)continue;
            var data=encodeView(view,pose,poseTick,frozen,"none".equals(view.session().dynamicPart()));
            result.add(new Prepared(view.dimension(),view.origin().add(torso.x(),torso.y(),torso.z()),observers,data));
            CorpsePerformance.count(CorpsePerformance.Counter.NETWORK_VIEW_ENCODED);
        }
        return result;
    }
    static NbtCompound encodeView(CorpsePhysicsCoordinator.LiveView view,com.kuilunfuzhe.monvhua.features.physics_preview.PhysicsPoseDocument pose,long poseTick,boolean frozen,boolean pinned){
        // Optional field: old servers omit it, updated clients then retain the previous interpolation behavior.
        var n=new NbtCompound();n.putString("id",view.display().toString());n.putString("victim",view.victim()==null?"":view.victim().toString());n.putDouble("x",view.origin().x);n.putDouble("y",view.origin().y);n.putDouble("z",view.origin().z);n.putBoolean("slim",view.slim());n.putLong("revision",frozen?0:view.committedRevision());n.putBoolean("frozen",frozen);n.putBoolean("pinned",pinned);n.putLong("tick",poseTick);n.putInt("step",pose.step());n.putString("definition",pose.definition());n.putFloat("scale",pose.scale());n.putLong("topology",com.kuilunfuzhe.monvhua.features.forensics.CorpsePoseRecord.data(view.skin()).getLong("corpse_topology_revision",0));
        n.putBoolean("resting",view.session().resting());
        var parts=new NbtList();for(var id:pose.poses().keySet()){var t=pose.poses().get(id);var part=new NbtCompound();part.putString("id",id);part.putFloat("x",t.x());part.putFloat("y",t.y());part.putFloat("z",t.z());part.putFloat("qx",t.qx());part.putFloat("qy",t.qy());part.putFloat("qz",t.qz());part.putFloat("qw",t.qw());parts.add(part);}n.put("parts",parts);return n;
    }
}
