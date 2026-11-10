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
    public static boolean validInput(double wheel,double x,double y,double z){return Double.isFinite(wheel)&&Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z)&&Math.abs(wheel)<=8&&x*x+y*y+z*z<=4.000001;}
    public static void initialize(){
        PayloadTypeRegistry.playS2C().register(Snapshot.ID,Snapshot.CODEC);PayloadTypeRegistry.playC2S().register(Input.ID,Input.CODEC);PayloadTypeRegistry.playC2S().register(Timeline.ID,Timeline.CODEC);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server->{lastInput.clear();lastAction.clear();});
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{var id=handler.player.getUuid();CorpseGrabController.release(id);CorpseTransportController.release(id);CorpseHistoryController.disconnect(id);lastInput.remove(id);lastAction.remove(id);});
        ServerPlayNetworking.registerGlobalReceiver(Input.ID,(packet,context)->context.server().execute(()->{
            var p=context.player();if(!p.isAlive()||p.isSpectator())return;
            var limits=packet.action.equals("input")?lastInput:lastAction;int now=context.server().getTicks();
            if(!packet.action.equals("release")&&limits.getOrDefault(p.getUuid(),-100)==now)return;limits.put(p.getUuid(),now);
            if(!validInput(packet.wheel,packet.x,packet.y,packet.z))return;
            try{switch(packet.action){
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
            var views=new ArrayList<>(CorpsePhysicsCoordinator.liveViews());
            for(var entry:CorpseHistoryController.entries())if(entry.valid&&entry.frozen()&&entry.view!=null&&views.stream().noneMatch(v->v.display().equals(entry.id)))views.add(entry.view);
            for(var player:server.getPlayerManager().getPlayerList()){
                if(!ServerPlayNetworking.canSend(player,Snapshot.ID))continue;
                var root=new NbtCompound();var list=new NbtList();root.putString("dimension",player.getWorld().getRegistryKey().getValue().toString());
                for(var view:views){
                    if(list.size()>=24||!view.dimension().equals(player.getWorld().getRegistryKey().getValue().toString()))continue;
                    var entity=player.getWorld().getEntity(view.display());if(entity==null||!PlayerLookup.tracking(entity).contains(player))continue;
                    var frames=view.session().published();if(frames.isEmpty())continue;var latest=frames.getLast();var pose=latest.pose();var torso=pose.poses().get("torso");if(torso==null||player.getPos().squaredDistanceTo(view.origin().add(torso.x(),torso.y(),torso.z()))>64*64)continue;
                    var history=CorpseHistoryController.get(view.display());boolean frozen=history!=null&&history.frozen();
                    long poseTick=latest.tick();
                    if(frozen){var selected=history.pose();if(selected!=null){pose=selected;poseTick=history.selectedFrame<0?history.last():history.nearest(history.selectedFrame);}}
                    var n=new NbtCompound();n.putString("id",view.display().toString());n.putString("victim",view.victim()==null?"":view.victim().toString());n.putDouble("x",view.origin().x);n.putDouble("y",view.origin().y);n.putDouble("z",view.origin().z);n.putBoolean("slim",view.slim());n.putLong("revision",frozen?0:view.committedRevision());n.putBoolean("frozen",frozen);n.putLong("tick",poseTick);n.putInt("step",pose.step());n.putString("definition",pose.definition());
                    var parts=new NbtList();for(var id:com.kuilunfuzhe.monvhua.features.physics_preview.PhysicsPoseDocument.IDS){var t=pose.poses().get(id);var part=new NbtCompound();part.putString("id",id);part.putFloat("x",t.x());part.putFloat("y",t.y());part.putFloat("z",t.z());part.putFloat("qx",t.qx());part.putFloat("qy",t.qy());part.putFloat("qz",t.qz());part.putFloat("qw",t.qw());parts.add(part);}n.put("parts",parts);list.add(n);
                }root.put("views",list);
                var status=CorpseGrabController.status(player.getUuid());if(status!=null){var n=new NbtCompound();n.putString("id",status.id().toString());n.putString("part",status.part());n.putBoolean("local",status.local());n.putBoolean("frozen",status.frozen());n.putDouble("distance",status.distance());root.put("grab",n);}
                var timeline=CorpseHistoryController.timeline(player.getUuid());if(timeline!=null){var n=new NbtCompound();n.putString("id",timeline.id.toString());n.putBoolean("confirmed",timeline.confirmed());n.putBoolean("manual",timeline.manual);n.putBoolean("active",timeline.active);n.putLong("selected",timeline.selectedFrame);n.putLongArray("steps",timeline.steps().stream().limit(721).mapToLong(Long::longValue).toArray());root.put("timeline",n);}
                ServerPlayNetworking.send(player,new Snapshot(root));
            }
        });
    }
}
