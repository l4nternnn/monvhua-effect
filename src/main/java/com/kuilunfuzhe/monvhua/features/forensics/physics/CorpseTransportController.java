package com.kuilunfuzhe.monvhua.features.forensics.physics;

import com.kuilunfuzhe.monvhua.features.forensics.*;
import com.kuilunfuzhe.monvhua.features.forensics.blood.BloodTraceService;
import com.kuilunfuzhe.monvhua.features.block.body.BodyPartManager;
import com.kuilunfuzhe.monvhua.features.carryentity.CarryManager;
import com.kuilunfuzhe.monvhua.features.gravity.GravityMagic;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.entity.decoration.InteractionEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.*;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;

/** Server carry authority; native mutation uses the existing ordered worker mailbox. */
public final class CorpseTransportController {
    public static final List<String> LIMBS=List.of("left_arm","right_arm","left_leg","right_leg");
    private static final Map<UUID,Transport> sessions=new java.util.concurrent.ConcurrentHashMap<>();
    private static final class Transport {
        ServerPlayerEntity player; ItemDisplayEntity display; CorpseHistoryController.Entry entry;
        String part; boolean carry,pickup; Vec3d target,previous; Quaternionf orientation; long sequence,tick=Long.MIN_VALUE; int blocked;
    }
    public static String mode(UUID player){var s=sessions.get(player);return s==null?"":s.pickup?"pickup":s.carry?"carry":"drag";}
    public static String part(UUID player){var s=sessions.get(player);return s==null?"":s.part;}
    public static void releaseTarget(UUID id){for(var s:List.copyOf(sessions.values()))if(s.display.getUuid().equals(id))release(s.player.getUuid());}
    public static boolean carrying(UUID player){return sessions.containsKey(player);}
    public static boolean held(UUID id){return sessions.values().stream().anyMatch(s->s.display.getUuid().equals(id));}
    public static boolean owns(UUID id,CorpsePhysicsBridge.Session session){return sessions.values().stream().anyMatch(s->s.display.getUuid().equals(id)&&s.entry.session==session);}
    private static boolean available(ServerPlayerEntity p){return p.isAlive()&&!p.isSpectator()&&!p.hasVehicle()
        &&p.getMainHandStack().isEmpty()&&p.getOffHandStack().isEmpty()
        &&!CarryManager.CARRIED_ENTITIES.containsKey(p)&&!CarryManager.CARRIED_BY.containsKey(p)
        &&BodyHitGeometry.normalGravity(GravityMagic.getSurfaceGravityDirection(p))
        &&ForensicConfig.current.enabled&&ForensicConfig.current.transfer.enabled&&ForensicConfig.current.transfer.carryEnabled;}
    public static String start(ServerPlayerEntity p,int target,String mode,String requested){
        if(!mode.equals("drag")&&!mode.equals("carry")&&!mode.equals("pickup"))return "未知搬运方式";
        if(!com.kuilunfuzhe.monvhua.features.carryentity.CarryEffort.canMoveCorpse(p,mode.equals("drag")
            ?com.kuilunfuzhe.monvhua.features.carryentity.CarryEffort.Mode.DRAG
            :com.kuilunfuzhe.monvhua.features.carryentity.CarryEffort.Mode.CARRY))return "体力不足或不可用，无法搬运遗体";
        if(!CorpsePhysicsCoordinator.localControl(p.getServer()))return "物理搬运需要可用的服务器物理核心";
        if(!available(p)||CorpseCarryService.isCarrying(p.getUuid())||CorpseGrabController.status(p.getUuid())!=null)return "当前状态不能搬运，请空出双手并放下已有对象";
        if(mode.equals("drag")&&p.getCommandTags().contains("monvhua:no_corpse_drag"))return "当前状态禁止拖拽";
        var entity=p.getWorld().getEntityById(target);
        if(entity instanceof InteractionEntity){var id=BodyPartManager.INTERACTION_TO_DISPLAY.get(entity.getUuid());entity=id==null?null:p.getWorld().getEntity(id);}
        if(!(entity instanceof ItemDisplayEntity d)||!Forensics.valid(p.getWorld(),d.getItemStack(),d.getUuid().toString()))return "请选择规范遗体或离体肢体";
        var record=CorpsePoseRecord.read(CorpsePoseRecord.data(d.getItemStack())).orElse(null);
        if(record==null||!permits(mode,requested,record.parts().keySet()))return "拾起仅适用于独立头部或四肢；完整遗体请使用抱起或拖拽";
        if(CorpseCarryService.isCarried(d)||CorpseGrabController.held(d.getUuid())||CorpsePhysicsCoordinator.preparing(d))return "尸体正在生成姿态、分离或被其他玩家操作";
        var selection=CorpseInspectionContext.find(p,d,requested);
        if(selection==null)return "本次部位选择已失效，请重新打开检查轮盘";
        var eye=selection.eye();
        var hit=CorpsePoseGeometry.pick(d,eye,eye.add(selection.direction().multiply(ForensicConfig.current.inspectionDistance)));
        if(hit==null||!hit.part().equals(requested))return "请重新瞄准所选部位";
        if(p.getWorld().raycast(new RaycastContext(eye,hit.position(),RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,p)).getType()!=HitResult.Type.MISS)return "尸体被遮挡";
        if(mode.equals("carry")&&!requested.equals("torso"))return "请瞄准躯干选择抱起";
        if(mode.equals("drag")&&!LIMBS.contains(requested)&&!requested.equals("torso"))return "请选择左臂、右臂、左腿或右腿";
        try{
            if(CorpseHistoryController.get(d.getUuid())==null||!CorpseResidentManager.contains(d.getUuid()))CorpseGrabController.seed(p,d);
            var e=CorpseHistoryController.get(d.getUuid());
            if(e==null||!e.current()||e.frozen()||!e.session.continuous())return "请结束冻结或时间轴操作后搬运";
            var state=e.session.residentState();
            if(state!=null&&(state.phase().equals("FAILED")||state.phase().equals("CLOSED")))return "尸体物理会话已失效";
            var s=new Transport();s.player=p;s.display=d;s.entry=e;s.pickup=mode.equals("pickup");s.carry=mode.equals("carry");s.part=s.carry?"torso":requested;
            if(s.part.equals("torso")&&!s.carry){
                s.part=LIMBS.stream().filter(id->frame(e).poses().containsKey(id)).filter(id->reachable(p,point(e,id,true))).min(Comparator.comparingDouble(id->p.getEyePos().squaredDistanceTo(point(e,id,true)))).orElseThrow(()->new IllegalArgumentException("没有可触及的四肢远端"));
            }
            s.target=point(e,s.part,!s.carry&&!s.pickup);
            if(!s.carry&&!reachable(p,s.target))return "该肢体远端过远或被遮挡，请靠近后重试";
            var initial=frame(e);s.orientation=initial.poses().get(initial.rootPart()).rotation();s.previous=point(e,initial.rootPart(),false);s.sequence=e.session.controlSequence();
            if((s.carry||s.pickup)&&!reachable(p,desired(s)))return "胸前承托空间受阻";
            sessions.put(p.getUuid(),s);send(s,true);CorpseResidentManager.manipulated(d.getUuid());BloodTraceService.beginCarry(d,p);pose(s,true);
            return s.pickup?"已拾起"+label(s.part)+"，在身前保持物理状态；通过轮盘放下":s.carry?"已开始抱起遗体":"已抓住"+label(s.part)+"，可拖动遗体";
        }catch(RuntimeException error){release(p.getUuid());return "搬运未开始："+error.getMessage();}
    }
    /** Mode/topology contract shared by server checks and regression tests. */
    public static boolean permits(String mode,String requested,Set<String> parts){
        if(!parts.contains(requested))return false;
        if(mode.equals("pickup"))return parts.size()==1&&!parts.contains("torso")&&(requested.equals("head")||LIMBS.contains(requested));
        return parts.contains("torso")&&(mode.equals("carry")&&requested.equals("torso")||mode.equals("drag")&&parts.size()>1);
    }
    private static String label(String p){return switch(p){case "head"->"头部";case "left_arm"->"左臂";case "right_arm"->"右臂";case "left_leg"->"左腿";default->"右腿";};}
    private static boolean reachable(ServerPlayerEntity p,Vec3d v){return p.getEyePos().distanceTo(v)<=ForensicConfig.current.inspectionDistance&&p.getWorld().raycast(new RaycastContext(p.getEyePos(),v,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,p)).getType()==HitResult.Type.MISS;}
    /** Seeded workers accept ordered control immediately, before publishing their first frame. */
    static com.kuilunfuzhe.monvhua.features.physics_preview.PhysicsPoseDocument transportFrame(CorpsePhysicsBridge.Session session,net.minecraft.item.ItemStack seed){
        var frames=session.frames();if(!frames.isEmpty())return frames.getLast();
        var record=CorpsePoseRecord.read(CorpsePoseRecord.data(seed)).orElseThrow(()->new IllegalArgumentException("缺少可搬运姿态"));
        return new com.kuilunfuzhe.monvhua.features.physics_preview.PhysicsPoseDocument("SETTLED",0,record.parts(),0,"transport seed",record.definition(),record.scale());
    }
    private static com.kuilunfuzhe.monvhua.features.physics_preview.PhysicsPoseDocument frame(CorpseHistoryController.Entry e){return transportFrame(e.session,e.view.skin());}
    private static Vec3d point(CorpseHistoryController.Entry e,String part,boolean distal){var frame=frame(e);var pose=frame.poses().get(part);var v=pose.rotation().transform(new Vector3f(0,distal?-.375f*frame.scale():0,0));return e.view.origin().add(pose.x()+v.x,pose.y()+v.y,pose.z()+v.z);}
    private static Vec3d desired(Transport s){double yaw=Math.toRadians(s.player.bodyYaw);var forward=new Vec3d(-Math.sin(yaw),0,Math.cos(yaw));var side=new Vec3d(Math.cos(yaw),0,Math.sin(yaw));return s.player.getPos().add(forward.multiply((s.carry||s.pickup)?.85:.65)).add(side.multiply(s.carry?0:s.pickup?.18:.32)).add(0,(s.carry||s.pickup)?1.2:.85,0);}
    private static void send(Transport s,boolean active){
        var t=s.target.subtract(s.entry.view.origin());var q=s.orientation;
        var support=active&&s.carry?new CorpsePhysicsBridge.Support(q.x,q.y,q.z,q.w):null;
        s.entry.session.control(new CorpsePhysicsBridge.Grab(++s.sequence,active?s.part:null,0,(s.carry||s.pickup)?0:-.375f*frame(s.entry).scale(),0,(float)t.x,(float)t.y,(float)t.z,0,0,0,false,null,support));
    }
    private static void pose(Transport s,boolean active){
        if(!s.carry&&!s.pickup)return;
        var packet=new com.kuilunfuzhe.monvhua.network.carryentity.CarryPoseSyncS2CPacket(s.player.getId(),active?1:0,-1);
        for(var viewer:s.player.getServer().getPlayerManager().getPlayerList())net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(viewer,packet);
    }
    public static void release(UUID player){var s=sessions.remove(player);if(s==null)return;com.kuilunfuzhe.monvhua.features.forensics.blood.BloodMotionEvidenceService.release(s.display.getUuid());try{if(s.entry.valid)send(s,false);}catch(RuntimeException error){CorpseResidentManager.remove(s.display.getUuid());}finally{pose(s,false);BloodTraceService.endCarry(s.display,s.player);}}
    public static void reset(){for(var id:List.copyOf(sessions.keySet()))release(id);}
    public static void tick(MinecraftServer server){for(var s:List.copyOf(sessions.values()))try{
        var p=server.getPlayerManager().getPlayer(s.player.getUuid());
        if(p!=s.player||!available(s.player)||!s.entry.current()||s.entry.frozen()||s.display.getWorld()!=s.player.getWorld()){release(s.player.getUuid());continue;}
        long now=p.getWorld().getTime();if(s.tick==now)continue;s.tick=now;
        var actual=point(s.entry,s.part,!s.carry&&!s.pickup);var next=desired(s);
        if(actual.distanceTo(p.getPos())>6||next.subtract(s.entry.view.origin()).length()>118){release(p.getUuid());p.sendMessage(net.minecraft.text.Text.literal("搬运距离达到限制，已松开遗体"),true);continue;}
        var path=p.getWorld().raycast(new RaycastContext(s.target,next,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,p));
        var wall=p.getWorld().raycast(new RaycastContext(p.getEyePos(),next,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,p));
        boolean obstructed=path.getType()!=HitResult.Type.MISS||wall.getType()!=HitResult.Type.MISS||s.target.distanceTo(actual)>1.8;
        s.blocked=obstructed?s.blocked+1:0;
        if(s.blocked>20){release(p.getUuid());p.sendMessage(net.minecraft.text.Text.literal("搬运受阻，已松开遗体"),true);continue;}
        if(!obstructed){var delta=next.subtract(s.target);s.target=s.target.add(delta.length()>.22?delta.normalize().multiply(.22):delta);}
        if(s.carry&&!obstructed){var goal=new Quaternionf().rotationY((float)Math.toRadians(-p.bodyYaw)).rotateZ((float)Math.PI/2);float angle=2*(float)Math.acos(Math.min(1,Math.abs(s.orientation.dot(goal))));s.orientation.slerp(goal,angle<.001f?1:Math.min(1,.06f/angle));}
        send(s,true);if(now%100==0)com.kuilunfuzhe.monvhua.features.forensics.blood.BloodTransferService.contact(s.display,p);var torso=point(s.entry,frame(s.entry).rootPart(),false);if(!com.kuilunfuzhe.monvhua.features.forensics.blood.BloodMotionEvidenceService.moved(s.display,s.entry))BloodTraceService.physicsMoved(s.display,s.previous,torso,p);s.previous=torso;
    }catch(RuntimeException error){release(s.player.getUuid());s.player.sendMessage(net.minecraft.text.Text.literal("搬运停止："+error.getMessage()),true);}}
}
