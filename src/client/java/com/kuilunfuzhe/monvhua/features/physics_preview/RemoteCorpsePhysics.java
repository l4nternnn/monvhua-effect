package com.kuilunfuzhe.monvhua.features.physics_preview;
import com.kuilunfuzhe.monvhua.features.forensics.physics.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.*;
import net.minecraft.util.math.Vec3d;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Client-only bounded pose cache; it never starts native physics on remote connections. */
public final class RemoteCorpsePhysics {
    private static final Map<UUID,CorpsePhysicsCoordinator.LiveView> views=new HashMap<>();
    private static CorpseGrabController.Status status;
    public record Timeline(UUID id,boolean confirmed,boolean manual,boolean active,long selected,long[] steps){}
    private static Timeline timeline;
    private static UUID pendingTimeline;
    public static Timeline timeline(){return System.nanoTime()-received>2_000_000_000L?null:timeline;}
    public static void timelineAction(String action,UUID target,long frame){
        if(action.equals("open"))pendingTimeline=target==null?new UUID(0,0):target;
        if(action.equals("close"))pendingTimeline=null;
        if(ClientPlayNetworking.canSend(CorpsePhysicsNetwork.Timeline.ID))ClientPlayNetworking.send(new CorpsePhysicsNetwork.Timeline(action,target==null?"":target.toString(),frame));
    }
    private static long received;
    private static Object world;
    private static final Map<UUID,Long> acknowledged=new HashMap<>();
    public static void tick(MinecraftClient client){if(world!=client.world){clear();world=client.world;}}

    public static void initialize(){ClientPlayNetworking.registerGlobalReceiver(CorpsePhysicsNetwork.Snapshot.ID,(packet,context)->context.client().execute(()->accept(packet.data())));}
    public static boolean remote(){return MinecraftClient.getInstance().getServer()==null;}
    public static List<CorpsePhysicsCoordinator.LiveView> views(){
        var values=remote()?(System.nanoTime()-received>2_000_000_000L?List.<CorpsePhysicsCoordinator.LiveView>of():List.copyOf(views.values())):CorpsePhysicsCoordinator.liveViews();
        var client=MinecraftClient.getInstance();if(client.world==null)return List.of();
        return values.stream().filter(v->{
            for(var entity:client.world.getEntities())if(entity instanceof ItemDisplayEntity d&&d.getUuid().equals(v.display())){
                var pose=com.kuilunfuzhe.monvhua.features.forensics.CorpsePoseRecord.read(com.kuilunfuzhe.monvhua.features.forensics.CorpsePoseRecord.data(d.getItemStack()));
                var frames=v.session().frames();return pose.isEmpty()||frames.isEmpty()||pose.get().parts().keySet().equals(frames.getLast().poses().keySet());
            }return false;
        }).toList();
    }
    public static boolean pinned(UUID id){
        if(!remote()){var e=CorpseHistoryController.get(id);return e!=null&&"none".equals(e.session.dynamicPart());}
        var v=views.get(id);return System.nanoTime()-received<=2_000_000_000L&&v!=null&&((Session)v.session()).pinned;
    }
    public static CorpseGrabController.Status status(UUID id){return remote()?(System.nanoTime()-received>2_000_000_000L?null:status):CorpseGrabController.status(id);}
    public static void clear(){views.clear();acknowledged.clear();status=null;timeline=null;pendingTimeline=null;received=0;world=null;}
    public static void acknowledge(UUID id,long revision){if(remote()){acknowledged.put(id,revision);views.remove(id);}}
    public static void action(String action,boolean partial,double wheel,Vec3d angular,boolean rotating){if(ClientPlayNetworking.canSend(CorpsePhysicsNetwork.Input.ID))ClientPlayNetworking.send(new CorpsePhysicsNetwork.Input(action,partial,wheel,angular.x,angular.y,angular.z,rotating));}
    public static List<CorpsePhysicsCoordinator.LiveView> overlayViews(){
        var result=new ArrayList<>(views());var c=MinecraftClient.getInstance();
        if(!remote()){
            for(var e:CorpseHistoryController.entries())if(e.valid&&e.view!=null&&result.stream().noneMatch(v->v.display().equals(e.id)))result.add(e.view);
        }else if(c.world!=null&&c.player!=null){
            for(var entity:c.world.getEntities())if(entity instanceof ItemDisplayEntity display&&display.squaredDistanceTo(c.player)<=16*16&&result.stream().noneMatch(v->v.display().equals(display.getUuid()))){
                // Only the neutral canonical carrier can be reconstituted directly from its persisted six-body pose.
                if(display.getYaw()!=0||display.getPitch()!=0||display.getItemDisplayContext()!=net.minecraft.item.ItemDisplayContext.NONE
                        ||!com.kuilunfuzhe.monvhua.mixin.forensics.CorpseDisplayAccessor.monvhua$transform(display.getDataTracker()).getMatrix().equals(new org.joml.Matrix4f(),1e-6f))continue;
                var record=com.kuilunfuzhe.monvhua.features.forensics.CorpsePoseRecord.read(com.kuilunfuzhe.monvhua.features.forensics.CorpsePoseRecord.data(display.getItemStack()));if(record.isEmpty())continue;
                var p=record.get();var session=new Session();session.add(0,new PhysicsPoseDocument("SETTLED",0,p.parts(),0,"overlay",p.definition(),p.scale()));
                result.add(new CorpsePhysicsCoordinator.LiveView(display.getUuid(),c.world.getRegistryKey().getValue().toString(),display.getPos(),display.getItemStack(),p.armModel().equals("slim"),session,null,p.revision()));
            }
        }return result;
    }
    public static CorpseGrabController.Hit pick(net.minecraft.entity.player.PlayerEntity player){
        var from=player.getEyePos();var to=from.add(player.getRotationVec(1).multiply(Math.min(6,player.getEntityInteractionRange())));
        var wall=player.getWorld().raycast(new net.minecraft.world.RaycastContext(from,to,net.minecraft.world.RaycastContext.ShapeType.COLLIDER,net.minecraft.world.RaycastContext.FluidHandling.NONE,player));
        if(wall.getType()!=net.minecraft.util.hit.HitResult.Type.MISS)to=wall.getPos();
        CorpseGrabController.Hit best=null;
        for(var view:overlayViews()){
            if(!view.dimension().equals(player.getWorld().getRegistryKey().getValue().toString()))continue;
            var frames=view.session().frames();if(frames.isEmpty())continue;
            for(var part:frames.getLast().poses().entrySet()){
                var p=part.getValue();var inverse=p.rotation().conjugate();
                var a=from.subtract(view.origin()).subtract(p.x(),p.y(),p.z());var b=to.subtract(view.origin()).subtract(p.x(),p.y(),p.z());
                var aa=inverse.transform(new org.joml.Vector3f((float)a.x,(float)a.y,(float)a.z));var bb=inverse.transform(new org.joml.Vector3f((float)b.x,(float)b.y,(float)b.z));
                var size=StandardBodyPhysicsAdapter.size(part.getKey(),view.slim()).mul(.5f*frames.getLast().scale());
                var box=new net.minecraft.util.math.Box(-size.x,-size.y,-size.z,size.x,size.y,size.z);var la=new Vec3d(aa.x,aa.y,aa.z);
                var hit=box.contains(la)?Optional.of(la):box.raycast(la,new Vec3d(bb.x,bb.y,bb.z));if(hit.isEmpty())continue;
                double distance=la.distanceTo(hit.get());if(best==null||distance<best.distance())best=new CorpseGrabController.Hit(view.display(),part.getKey(),hit.get(),from.add(to.subtract(from).normalize().multiply(distance)),distance);
            }
        }return best;
    }
    private static void accept(NbtCompound data){
        if(!remote())return;var c=MinecraftClient.getInstance();if(c.world==null||!c.world.getRegistryKey().getValue().toString().equals(data.getString("dimension","")))return;received=System.nanoTime();
        var keep=new HashSet<UUID>();
        for(var element:data.getListOrEmpty("views")){if(!(element instanceof NbtCompound n)||keep.size()>=24)continue;
            try{UUID id=UUID.fromString(n.getString("id",""));if(n.getLong("revision",0)>0&&acknowledged.getOrDefault(id,-1L)==n.getLong("revision",0)){keep.add(id);continue;}ItemDisplayEntity display=null;for(var entity:c.world.getEntities())if(entity.getUuid().equals(id)&&entity instanceof ItemDisplayEntity d){display=d;break;}if(display==null)continue;
            var parts=new LinkedHashMap<String,PhysicsPoseDocument.Pose>();for(var item:n.getListOrEmpty("parts"))if(item instanceof NbtCompound p)parts.put(p.getString("id",""),new PhysicsPoseDocument.Pose(p.getFloat("x",0),p.getFloat("y",0),p.getFloat("z",0),p.getFloat("qx",0),p.getFloat("qy",0),p.getFloat("qz",0),p.getFloat("qw",1)));
            PhysicsPoseDocument.validateParts(parts.keySet());
            var old=views.get(id);long topology=n.getLong("topology",0);if(topology<0||old!=null&&((Session)old.session()).topology>topology){keep.add(id);continue;}var session=old==null||((Session)old.session()).topology!=topology||old.session().frames().isEmpty()||!old.session().frames().getLast().poses().keySet().equals(parts.keySet())?new Session():(Session)old.session();
            session.topology=topology;var pose=new PhysicsPoseDocument("SIMULATING",n.getInt("step",0),parts,0,"server",n.getString("definition","standard_six_v1"),n.getFloat("scale",1));session.frozen=n.getBoolean("frozen",false);session.pinned=n.getBoolean("pinned",false);session.add(n.getLong("tick",0),pose);
            String victim=n.getString("victim","");views.put(id,new CorpsePhysicsCoordinator.LiveView(id,data.getString("dimension",""),new Vec3d(n.getDouble("x",0),n.getDouble("y",0),n.getDouble("z",0)),display.getItemStack().copy(),n.getBoolean("slim",false),session,victim.isEmpty()?null:UUID.fromString(victim),n.getLong("revision",0)));keep.add(id);
        }catch(IllegalArgumentException ignored){} }views.keySet().retainAll(keep);acknowledged.keySet().retainAll(keep);
        var timelineData=data.getCompoundOrEmpty("timeline");timeline=timelineData.isEmpty()?null:new Timeline(UUID.fromString(timelineData.getString("id","")),timelineData.getBoolean("confirmed",false),timelineData.getBoolean("manual",false),timelineData.getBoolean("active",false),timelineData.getLong("selected",-1),timelineData.getLongArray("steps").orElse(new long[0]));
        if(timeline!=null&&pendingTimeline!=null&&c.currentScreen==null){pendingTimeline=null;c.setScreen(new RemoteCorpseTimelineScreen(timeline.id()));}
        status=CorpsePhysicsNetwork.decodeGrabStatus(data.getCompoundOrEmpty("grab"));
    }
    private static final class Session implements CorpsePhysicsBridge.Session {
        private boolean frozen,pinned;private long topology;
        public boolean paused(){return frozen;}
        private final List<CorpsePhysicsBridge.TimedPose> frames=new ArrayList<>();
        void add(long tick,PhysicsPoseDocument p){if(!frames.isEmpty()&&frames.getLast().tick()>tick)frames.clear();if(!frames.isEmpty()&&frames.getLast().tick()==tick)frames.removeLast();frames.add(new CorpsePhysicsBridge.TimedPose(tick,p));while(frames.size()>8)frames.removeFirst();}
        public List<PhysicsPoseDocument> frames(){return frames.stream().map(CorpsePhysicsBridge.TimedPose::pose).toList();}
        public List<CorpsePhysicsBridge.TimedPose> published(){return List.copyOf(frames);}
        public void advanceTo(int step){}public CompletableFuture<PhysicsPoseDocument> completion(){return new CompletableFuture<>();}public void close(){frames.clear();}
    }
}
