package com.kuilunfuzhe.monvhua.features.forensics.blood;
import com.kuilunfuzhe.monvhua.features.forensics.*;
import com.kuilunfuzhe.monvhua.features.forensics.physics.*;
import com.kuilunfuzhe.monvhua.features.physics_preview.PhysicsPoseDocument;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import org.joml.Vector3f;
import java.util.*;
/** Consumes forward authoritative physical poses, never requested targets or historical replay. */
public final class BloodMotionEvidenceService {
    private record Sample(CorpsePhysicsBridge.Session session,long generation,long tick,long time,Map<String,Vec3d> contacts){}
    private static final Map<UUID,Sample> LAST=new HashMap<>();
    private BloodMotionEvidenceService(){}
    public static void release(UUID id){LAST.remove(id);}
    public static void reset(){LAST.clear();}
    public static boolean moved(ItemDisplayEntity d,CorpseHistoryController.Entry entry){
        if(!ForensicConfig.current.cleaning.dragTraces||!BloodRules.generating()||!BloodTransferService.enabled()||entry.frozen()||!entry.current()){release(d.getUuid());return false;}
        var frames=entry.session.published();if(frames.isEmpty())return false;
        var frame=frames.getLast();var state=entry.session.residentState();if(state==null)return false;
        var world=(ServerWorld)d.getWorld();long now=BloodTraceService.now(world.getServer());
        var old=LAST.get(d.getUuid());
        if(old!=null&&old.session==entry.session&&old.generation==state.generation()&&frame.tick()<=old.tick)return false;
        if(old!=null&&now-old.time<5)return false;
        var points=contacts(world,d,frame.pose(),entry.view.origin());
        LAST.put(d.getUuid(),new Sample(entry.session,state.generation(),frame.tick(),now,points));
        if(old==null||old.session!=entry.session||old.generation!=state.generation()||now-old.time>20)return false;
        var binding=Forensics.binding(d.getItemStack());
        if(!Forensics.valid(world,d.getItemStack(),d.getUuid().toString()))return false;
        var fs=ForensicStore.get(world.getServer());var corpse=fs.corpse(binding.getString("id",""));var ledger=corpse.getCompoundOrEmpty("blood");
        var transfers=BloodTransferStore.get(world.getServer());boolean generated=false;
        for(var point:points.entrySet()){
            Vec3d previous=old.contacts.get(point.getKey()),current=point.getValue();
            if(previous==null||previous.distanceTo(current)<.04||previous.distanceTo(current)>.6||Math.abs(previous.y-current.y)>.02)continue;
            String part=point.getKey(),slot=switch(part){case "torso"->"torso";case "left_arm","right_arm"->"hands";case "left_leg"->"left_sole";case "right_leg"->"right_sole";default->"";};
            String holder=corpse.getString("id","")+"/"+slot;
            // Pick one source. Terminal trails never receive transferable batches.
            boolean stained=!slot.isEmpty()&&transfers.available(holder,now)>0;
            if(!stained&&(!BloodRules.active(ledger,now)||BloodRules.remaining(ledger,part)<=0))continue;
            int pieces=Math.max(1,(int)Math.ceil(previous.distanceTo(current)/.15));
            for(int i=0;i<pieces;i++){
                Vec3d a=previous.lerp(current,i/(double)pieces),b=previous.lerp(current,(i+1)/(double)pieces),mid=a.lerp(b,.5);
                var hit=BloodSurfaceProjection.cast(world,mid.add(0,.04,0),mid.add(0,-.04,0),d);
                if(hit==null||hit.getSide()!=Direction.UP)continue;
                // Crossing a face boundary is broken, never painted through a wall or gap.
                if(!BlockPos.ofFloored(a.add(0,-.02,0)).equals(hit.getBlockPos())||!BlockPos.ofFloored(b.add(0,-.02,0)).equals(hit.getBlockPos()))continue;
                if(stained?transfers.available(holder,now)<=0:BloodRules.remaining(ledger,part)<=0)break;
                var shape=BloodSurfaceProjection.impact(hit,(float)Math.min(.25,a.distanceTo(b)/2+.025),0);
                Vec3d delta=b.subtract(a);double angle=Math.atan2(delta.dotProduct(shape.down()),delta.dotProduct(shape.right()));
                long seed=Math.round((angle+Math.PI)/(Math.PI*2)*65535)&65535L;
                var patch=new BloodPatch(shape.id(),shape.pos(),shape.face(),shape.u(),shape.v(),shape.radius(),4,seed,1);
                String source=stained?transfers.sourceSummary(holder,now):corpse.getString("id","");
                if(BloodTraceStore.get(world).submit(new BloodTraceStore.Entry(patch,source,UUID.randomUUID().toString(),world.getBlockState(patch.pos()).toString(),false))){
                    if(stained)transfers.consume(holder,1,now);else{BloodRules.consume(ledger,part,1);fs.markDirty();}generated=true;
                }
            }
        }
        return generated;
    }
    private static Map<String,Vec3d> contacts(ServerWorld world,ItemDisplayEntity d,PhysicsPoseDocument pose,Vec3d origin){
        Map<String,Vec3d> result=new HashMap<>();
        for(var e:pose.poses().entrySet()){
            String part=e.getKey();var p=e.getValue();
            float hx=part.equals("torso")||part.equals("head")?.25f:part.endsWith("arm")&&pose.definition().startsWith("slim")?.09375f:.125f;
            float hy=part.equals("head")?.25f:.375f,hz=part.equals("head")?.25f:.125f;
            var corners=new ArrayList<Vec3d>();double low=Double.POSITIVE_INFINITY;
            for(int i=0;i<8;i++){
                var v=p.rotation().transform(new Vector3f((i&1)==0?-hx:hx,(i&2)==0?-hy:hy,(i&4)==0?-hz:hz));
                var q=origin.add(p.x()+v.x,p.y()+v.y,p.z()+v.z);corners.add(q);low=Math.min(low,q.y);
            }
            Vec3d sum=Vec3d.ZERO;int count=0;for(var c:corners)if(c.y-low<.015){sum=sum.add(c);count++;}
            Vec3d point=sum.multiply(1.0/count);
            if(!world.isChunkLoaded(BlockPos.ofFloored(point)))continue;
            var hit=BloodSurfaceProjection.cast(world,point.add(0,.03,0),point.add(0,-.03,0),d);
            if(hit!=null&&hit.getSide()==Direction.UP&&Math.abs(hit.getPos().y-point.y)<=.03)result.put(part,hit.getPos());
        }
        return result;
    }
}
