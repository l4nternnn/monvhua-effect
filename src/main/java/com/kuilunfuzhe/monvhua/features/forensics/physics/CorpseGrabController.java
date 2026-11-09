package com.kuilunfuzhe.monvhua.features.forensics.physics;

import com.kuilunfuzhe.monvhua.features.forensics.*;
import com.kuilunfuzhe.monvhua.features.physics_preview.*;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;
import org.joml.Vector3f;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** All writes occur on the integrated server. Client sees immutable status only. */
public final class CorpseGrabController {
    private static final Identifier ID=Identifier.of("monvhua","corpse_physics_grabber");
    public static final Item ITEM=Registry.register(Registries.ITEM,ID,new Item(new Item.Settings().registryKey(RegistryKey.of(RegistryKeys.ITEM,ID)).maxCount(1)));
    public record Hit(UUID id,String part,Vec3d local,Vec3d world,double distance){}
    public record Status(UUID id,String part,boolean local,boolean frozen,double distance,Vec3d actual,Vec3d target,String message){}
    private static final Map<UUID,Grab> grabs=new ConcurrentHashMap<>();
    private static final Map<UUID,Status> status=new ConcurrentHashMap<>();
    private record StaticConfirmation(UUID target,CorpsePhysicsBridge.Session session,long tick,long sequence,boolean pinned,long expires){}
    private record PendingPin(CorpseHistoryController.Entry entry,long sequence){}
    private static final Map<UUID,PendingPin> pendingPins=new ConcurrentHashMap<>();
    private static boolean pinPending(CorpseHistoryController.Entry e){var pin=pendingPins.get(e.id);return pin!=null&&pin.entry()==e&&e.session.controlSequence()<pin.sequence();}
    private static final Map<UUID,StaticConfirmation> staticConfirmations=new ConcurrentHashMap<>();
    private static final class Grab {
        UUID player,id;String part;Vec3d local,target;double distance;boolean partial,rotating,wasFrozen;Vec3d angular=Vec3d.ZERO;long sequence,lastInput,processedTick=Long.MIN_VALUE;ItemDisplayEntity display;Vec3d previousTorso;
        CorpseHistoryController.Entry entry;
    }
    public static void initialize(){}
    public static boolean held(UUID id){return grabs.values().stream().anyMatch(g->g.id.equals(id));}
    public static Status status(UUID player){return status.get(player);}
    public static void reset(){pendingPins.clear();staticConfirmations.clear();for(var id:List.copyOf(grabs.keySet()))release(id);status.clear();}
    private static void require(ServerPlayerEntity p){if(CorpseTransportController.carrying(p.getUuid()))throw new IllegalArgumentException("请先放下搬运对象");if(!p.hasPermissionLevel(2)||!p.getMainHandStack().isOf(ITEM)||!CorpsePhysicsCoordinator.localControl(p.getServer()))throw new IllegalArgumentException("物理抓取工具仅供本地单人管理员使用");}
    public static Hit pick(net.minecraft.entity.player.PlayerEntity player){
        var from=player.getEyePos();var to=from.add(player.getRotationVec(1).multiply(Math.min(6,player.getEntityInteractionRange())));
        var wall=player.getWorld().raycast(new RaycastContext(from,to,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,player));if(wall.getType()!=HitResult.Type.MISS)to=wall.getPos();
        Hit best=null;
        for(var e:CorpseHistoryController.entries()){
            if(!e.valid||e.view==null||!e.view.dimension().equals(player.getWorld().getRegistryKey().getValue().toString()))continue;
            var frames=e.session.frames();if(frames.isEmpty())continue;var pose=frames.getLast();
            for(var part:pose.poses().entrySet()){
                var p=part.getValue();var inv=p.rotation().conjugate();var a=from.subtract(e.view.origin()).subtract(p.x(),p.y(),p.z());var b=to.subtract(e.view.origin()).subtract(p.x(),p.y(),p.z());
                var aa=inv.transform(new Vector3f((float)a.x,(float)a.y,(float)a.z));var bb=inv.transform(new Vector3f((float)b.x,(float)b.y,(float)b.z));
                var size=StandardBodyPhysicsAdapter.size(part.getKey(),e.view.slim()).mul(.5f);var box=new Box(-size.x,-size.y,-size.z,size.x,size.y,size.z);
                var la=new Vec3d(aa.x,aa.y,aa.z);var hit=box.contains(la)?Optional.of(la):box.raycast(la,new Vec3d(bb.x,bb.y,bb.z));
                if(hit.isEmpty())continue;double distance=la.distanceTo(hit.get());
                if(best==null||distance<best.distance())best=new Hit(e.id,part.getKey(),hit.get(),from.add(to.subtract(from).normalize().multiply(distance)),distance);
            }
        }
        return best;
    }
    public static void toggle(ServerPlayerEntity p,boolean partial){
        clearStaticConfirmation(p.getUuid());require(p);if(grabs.containsKey(p.getUuid())){release(p.getUuid());return;}
        var hit=pick(p);
        if(hit==null){
            // Import only canonical generated six-body poses; arbitrary edited geometry fails closed.
            var from=p.getEyePos();var to=from.add(p.getRotationVec(1).multiply(Math.min(6,p.getEntityInteractionRange())));
            var wall=p.getWorld().raycast(new RaycastContext(from,to,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,p));if(wall.getType()!=HitResult.Type.MISS)to=wall.getPos();
            ItemDisplayEntity nearest=null;double d=Double.POSITIVE_INFINITY;
            for(var entity:p.getWorld().getEntitiesByClass(ItemDisplayEntity.class,new Box(from,to).expand(3),e->Forensics.hasBinding(e.getItemStack()))){var h=CorpsePoseGeometry.pick(entity,from,to);if(h!=null&&h.position().distanceTo(from)<d){d=h.position().distanceTo(from);nearest=entity;}}
            if(nearest==null)throw new IllegalArgumentException("请瞄准完整尸体的表面");
            seed(p,nearest);throw new IllegalArgumentException("已接入当前姿态，待稳定后再次右键抓取");
        }
        var e=CorpseHistoryController.get(hit.id());
        if(e==null||!e.session.continuous()||e.frozen()||e.timeline)throw new IllegalArgumentException("请先结束冻结/时间轴并等待尸体稳定");
        var phase=e.session.residentState().phase();
        if(pinPending(e))throw new IllegalArgumentException("正在固定肢体，请稍候再次抓取");
        if(!phase.equals("SLEEPING")&&!phase.equals("SUPPORTED")&&!phase.equals("PINNED"))throw new IllegalArgumentException("尸体仍在运动，请先冻结当前肢体");
        String slot=e.session.dynamicPart();
        if(partial&&!hit.part().equals("torso")&&slot!=null&&!slot.equals("none")&&!slot.equals(hit.part()))
            throw new IllegalArgumentException("当前活动槽位仍由其他肢体占用，请先按 F 固定该肢体");
        if(grabs.values().stream().anyMatch(g->g.id.equals(e.id)))throw new IllegalArgumentException("尸体已被抓取");
        var entity=p.getWorld().getEntity(e.id);
        if(!(entity instanceof ItemDisplayEntity display)||!Forensics.valid(p.getWorld(),display.getItemStack(),e.id.toString())||CorpseCarryService.isCarried(display)||!Forensics.binding(display.getItemStack()).getString("part","").equals("all"))throw new IllegalArgumentException("尸体载体不可用");
        var g=new Grab();g.player=p.getUuid();g.id=e.id;g.display=display;g.part=hit.part();g.local=hit.local();g.distance=hit.distance();g.target=hit.world();g.entry=e;g.partial=partial&&!g.part.equals("torso");g.sequence=e.session.controlSequence();g.lastInput=p.getWorld().getTime();
        g.previousTorso=torso(g);com.kuilunfuzhe.monvhua.features.forensics.blood.BloodTraceService.beginPhysicsMove(display);grabs.put(g.player,g);CorpseResidentManager.manipulated(g.id);send(g,true);publish(g,hit.world(),"已抓取");
    }
    static void seed(ServerPlayerEntity p,ItemDisplayEntity d){
        if(CorpsePhysicsCoordinator.pending(d)||CorpseResidentManager.contains(d.getUuid())||CorpseCarryService.isCarried(d)||!Forensics.valid(p.getWorld(),d.getItemStack(),d.getUuid().toString())||!Forensics.binding(d.getItemStack()).getString("part","").equals("all"))throw new IllegalArgumentException("尸体暂不可接管");
        var data=CorpsePoseRecord.data(d.getItemStack());
        if(data.getFloat("corpse_scale",1)!=1||data.getString("body_pose_mode","").equals("true_skeletal"))throw new IllegalArgumentException("非标准模型暂不支持抓取");
        for(String key:data.getKeys())if((key.startsWith("pose_")||key.startsWith("bend_"))&&data.getFloat(key,Float.NaN)!=(key.endsWith("_scale")?1:0))throw new IllegalArgumentException("非中性模型编辑暂不支持抓取："+key);
        var record=CorpsePoseRecord.read(CorpsePoseRecord.data(d.getItemStack())).orElseThrow(()->new IllegalArgumentException("该尸体没有六部位物理姿态，请先生成物理姿态"));
        var matrix=com.kuilunfuzhe.monvhua.mixin.forensics.CorpseDisplayAccessor.monvhua$transform(d.getDataTracker()).getMatrix();
        if(d.getYaw()!=0||d.getPitch()!=0||!matrix.equals(new org.joml.Matrix4f(),1e-6f)||d.getItemDisplayContext()!=ItemDisplayContext.NONE)throw new IllegalArgumentException("该载体有额外模型变换，暂不支持抓取");
        var request=new CorpsePhysicsBridge.Request(record.armModel().equals("slim"),0,0,CorpseTerrainSnapshot.capture(p.getWorld(),d.getPos()));
        var pose=new PhysicsPoseDocument("SETTLED",0,record.parts(),0,"static seed",record.definition());
        if(!CorpseResidentManager.open(d,d.getPos(),request,CorpsePhysicsCoordinator.diagnosticProvider(p.getServer()),null,pose))throw new IllegalArgumentException("接入失败，保留旧姿态");
    }
    public static void input(ServerPlayerEntity p,double scroll,Vec3d angular,boolean rotating){
        require(p);var g=grabs.get(p.getUuid());if(g==null)return;g.lastInput=p.getWorld().getTime();
        if(!Double.isFinite(scroll)||!Double.isFinite(angular.lengthSquared()))return;
        if(g.entry.frozen()){g.angular=Vec3d.ZERO;g.rotating=false;return;}
        g.distance=Math.clamp(g.distance-Math.clamp(scroll,-4,4)*.25,.75,6);
        g.angular=angular.length()>2?angular.normalize().multiply(2):angular;g.rotating=rotating;
    }
    public static void range(ServerPlayerEntity p){clearStaticConfirmation(p.getUuid());require(p);var g=grabs.get(p.getUuid());if(g!=null){g.partial=!g.partial&&!g.part.equals("torso");send(g,true);}else{
        var hit=pick(p);if(hit!=null){var e=CorpseHistoryController.get(hit.id());if(e!=null&&!pinPending(e)&&!e.frozen()&&(e.session.residentState().phase().equals("SUPPORTED")||e.session.residentState().phase().equals("PINNED")))e.session.control(new CorpsePhysicsBridge.Grab(e.session.controlSequence()+1,null,0,0,0,0,0,0,0,0,0,false,null));}
    }}
    /** Each call represents a distinct key-down edge, not key repeat. */
    public static void staticPress(ServerPlayerEntity p){
        var previous=staticConfirmations.remove(p.getUuid());
        require(p);
        var g=grabs.get(p.getUuid());var hit=g==null?pick(p):null;
        UUID target=g!=null?g.id:hit==null?null:hit.id();
        var e=target==null?null:CorpseHistoryController.get(target);
        boolean pinned=e!=null&&e.session!=null&&e.session.residentState()!=null&&e.session.residentState().phase().equals("PINNED")&&!pinPending(e);
        if(e==null||e.view==null||e.session==null||!e.session.continuous()||!e.current()
                ||(!pinned&&(!e.manual||!e.confirmed()))||e.timeline||!CorpseResidentManager.contains(target))
            throw new IllegalArgumentException("请先用 F 全冻结尸体，等待冻结生效后再双击转静态键");
        var state=e.session.residentState();
        if(state==null||(!pinned&&!state.paused())||state.phase().equals("FAILED")||state.phase().equals("CLOSED"))throw new IllegalArgumentException("物理会话已失效");
        var entity=p.getWorld().getEntity(target);
        if(!(entity instanceof ItemDisplayEntity display)||!Forensics.valid(p.getWorld(),display.getItemStack(),target.toString())||CorpseCarryService.isCarried(display))throw new IllegalArgumentException("尸体载体已失效");
        if(!e.view.dimension().equals(p.getWorld().getRegistryKey().getValue().toString()))throw new IllegalArgumentException("尸体不在当前维度");
        var frames=e.session.frames();if(frames.isEmpty())throw new IllegalArgumentException("没有可保存的冻结姿态");
        var frame=frames.getLast();var torso=frame.poses().get("torso");
        if(e.view.origin().add(torso.x(),torso.y(),torso.z()).distanceTo(p.getEyePos())>8)throw new IllegalArgumentException("尸体距离过远");
        long now=System.nanoTime();
        if(previous==null||!previous.target().equals(target)||previous.session()!=e.session||previous.pinned()!=pinned||previous.sequence()!=e.session.controlSequence()||(!pinned&&previous.tick()!=state.tick())||now>previous.expires()){
            staticConfirmations.put(p.getUuid(),new StaticConfirmation(target,e.session,state.tick(),e.session.controlSequence(),pinned,now+600_000_000L));
            p.sendMessage(net.minecraft.text.Text.literal("请在 0.6 秒内再次按下转静态键，将保存当前冻结姿态"),true);return;
        }
        // Normalize only the coordinate origin; all world-space part transforms remain unchanged.
        var center=new Vec3d(Math.floor(torso.x()),Math.floor(torso.y()),Math.floor(torso.z()));
        var poses=new LinkedHashMap<String,PhysicsPoseDocument.Pose>();
        for(var part:frame.poses().entrySet()){var v=part.getValue();poses.put(part.getKey(),new PhysicsPoseDocument.Pose(v.x()-(float)center.x,v.y()-(float)center.y,v.z()-(float)center.z,v.qx(),v.qy(),v.qz(),v.qw()));}
        long revision=CorpsePoseRecord.data(display.getItemStack()).getLong("corpse_pose_revision",0)+1;
        var record=new CorpsePoseRecord(revision,"frozen_static",e.view.slim()?"slim":"default",poses,frame.definition());
        CorpsePoseService.applyFrozen(display,record,e.view.origin().add(center));
        // Commit first. A rejected candidate leaves the frozen live session intact.
        releaseTarget(target);CorpseResidentManager.remove(target);
        // Keep the exact frozen frame until the tracked static revision reaches the client.
        CorpsePhysicsCoordinator.publish(new CorpsePhysicsCoordinator.LiveView(target,e.view.dimension(),e.view.origin(),display.getItemStack().copy(),e.view.slim(),e.session,null,revision));
        p.sendMessage(net.minecraft.text.Text.literal("已保留当前姿态转为静态，物理计算已结束"),true);
    }
    public static void clearStaticConfirmation(UUID player){staticConfirmations.remove(player);}
    public static void freeze(ServerPlayerEntity p){freeze(p,false);}
    public static void freeze(ServerPlayerEntity p,boolean partialMode){
        clearStaticConfirmation(p.getUuid());require(p);var g=grabs.get(p.getUuid());
        if(g!=null&&g.partial&&!g.entry.frozen()){
            pin(p,g.entry,g.sequence);return;
        }
        if(g==null){
            var h=pick(p);if(h==null)return;var e=CorpseHistoryController.get(h.id());
            if(e!=null&&partialMode&&!e.frozen()&&e.session.dynamicPart()!=null){pin(p,e,e.session.controlSequence());return;}
            CorpseHistoryController.operate(p,h.id(),false);return;
        }
        var e=g.entry;if(!e.manual){e.manual=true;e.session.setPaused(true);}else{if(!CorpsePhysicsCoordinator.validateResume(e.id))return;e.manual=false;g.wasFrozen=true;e.session.setPaused(e.frozen());}g.angular=Vec3d.ZERO;g.rotating=false;
    }
    private static void pin(ServerPlayerEntity p,CorpseHistoryController.Entry e,long sequence){
        if(!e.current()||e.timeline||!e.session.continuous())throw new IllegalArgumentException("当前尸体不能固定肢体");
        if(pinPending(e))return;
        long next=Math.max(sequence,e.session.controlSequence())+1;
        // Queue a zero-slot state first, then drop ownership WITHOUT sending the ordinary release state.
        e.session.control(new CorpsePhysicsBridge.Grab(next,null,0,0,0,0,0,0,0,0,0,false,"none"));
        pendingPins.put(e.id,new PendingPin(e,next));
        var g=grabs.remove(p.getUuid());status.remove(p.getUuid());
        if(g!=null)com.kuilunfuzhe.monvhua.features.forensics.blood.BloodTraceService.endCarry(g.display,null);
        p.sendMessage(net.minecraft.text.Text.literal("已固定当前肢体并释放活动槽位，可右键选择其他肢体"),true);
    }
    public static void releaseTarget(UUID id){for(var g:List.copyOf(grabs.values()))if(g.id.equals(id))release(g.player);}
    public static void release(UUID player){clearStaticConfirmation(player);var g=grabs.remove(player);status.remove(player);if(g!=null){com.kuilunfuzhe.monvhua.features.forensics.blood.BloodTraceService.endCarry(g.display,null);if(g.entry.valid)try{send(g,false);}catch(RuntimeException error){CorpseResidentManager.remove(g.id);}}}
    private static void send(Grab g,boolean held){var target=g.target.subtract(g.entry.view.origin());g.entry.session.control(new CorpsePhysicsBridge.Grab(++g.sequence,held?g.part:null,(float)g.local.x,(float)g.local.y,(float)g.local.z,(float)target.x,(float)target.y,(float)target.z,(float)g.angular.x,(float)g.angular.y,(float)g.angular.z,g.rotating,g.partial?g.part:null));}
    private static Vec3d torso(Grab g){var frames=g.entry.session.frames();if(frames.isEmpty())return g.display.getPos();var p=frames.getLast().poses().get("torso");return g.entry.view.origin().add(p.x(),p.y(),p.z());}
    private static Vec3d actual(Grab g){var frames=g.entry.session.frames();if(frames.isEmpty())return g.target;var p=frames.getLast().poses().get(g.part);var v=p.rotation().transform(new Vector3f((float)g.local.x,(float)g.local.y,(float)g.local.z));return g.entry.view.origin().add(p.x()+v.x,p.y()+v.y,p.z()+v.z);}
    private static void publish(Grab g,Vec3d actual,String message){status.put(g.player,new Status(g.id,g.part,g.partial,g.entry.frozen(),g.distance,actual,g.target,message));}
    public static void tick(MinecraftServer server){pendingPins.entrySet().removeIf(e->!e.getValue().entry().valid||e.getValue().entry().session.controlSequence()>=e.getValue().sequence());staticConfirmations.entrySet().removeIf(e->System.nanoTime()>e.getValue().expires());for(var g:List.copyOf(grabs.values()))try{
        var p=server.getPlayerManager().getPlayer(g.player);
        if(p==null||!p.isAlive()||!p.getMainHandStack().isOf(ITEM)||!g.entry.valid||!g.entry.view.dimension().equals(p.getWorld().getRegistryKey().getValue().toString())||p.getWorld().getTime()-g.lastInput>20){release(g.player);continue;}
        long now=p.getWorld().getTime();if(now==g.processedTick)continue;g.processedTick=now;
        var actual=actual(g);if(g.entry.frozen()){g.wasFrozen=true;publish(g,actual,"冻结");continue;}
        if(g.wasFrozen){g.target=actual;g.distance=Math.clamp(p.getEyePos().distanceTo(actual),.75,6);g.wasFrozen=false;g.angular=Vec3d.ZERO;g.rotating=false;}
        var desired=p.getEyePos().add(p.getRotationVec(1).multiply(g.distance));
        var wall=p.getWorld().raycast(new RaycastContext(p.getEyePos(),desired,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,p));if(wall.getType()!=HitResult.Type.MISS)desired=wall.getPos().subtract(p.getRotationVec(1).multiply(.15));
        if(actual.distanceTo(p.getEyePos())>8||g.target.distanceTo(actual)>2.5){release(g.player);p.sendMessage(net.minecraft.text.Text.literal("抓取距离过大，已解除牵引"),true);continue;}
        var delta=desired.subtract(g.target);g.target=g.target.add(delta.length()>.12?delta.normalize().multiply(.12):delta);
        var currentTorso=torso(g);com.kuilunfuzhe.monvhua.features.forensics.blood.BloodTraceService.physicsMoved(g.display,g.previousTorso,currentTorso,p);g.previousTorso=currentTorso;
        send(g,true);publish(g,actual,g.partial?"局部":"整体");
        g.angular=Vec3d.ZERO;
    }catch(RuntimeException ex){release(g.player);var p=server.getPlayerManager().getPlayer(g.player);if(p!=null)p.sendMessage(net.minecraft.text.Text.literal("抓取停止："+ex.getMessage()),false);}}
}
