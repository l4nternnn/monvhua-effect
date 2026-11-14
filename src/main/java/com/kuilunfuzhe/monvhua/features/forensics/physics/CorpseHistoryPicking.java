package com.kuilunfuzhe.monvhua.features.forensics.physics;

import com.kuilunfuzhe.monvhua.features.forensics.*;
import com.kuilunfuzhe.monvhua.features.physics_preview.StandardBodyPhysicsAdapter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.hit.HitResult;
import org.joml.Vector3f;

public final class CorpseHistoryPicking {
    public static boolean reachable(PlayerEntity player,CorpseHistoryController.Entry entry){
        Vec3d target;
        if(entry.view!=null){
            var pose=entry.pose();if(pose==null){var frames=entry.session.frames();if(frames.isEmpty())return false;pose=frames.getLast();}
            var torso=pose.poses().get(pose.rootPart());target=entry.view.origin().add(torso.x(),torso.y(),torso.z());
        }else target=entry.targetPoint();
        var from=player.getEyePos();if(from.distanceTo(target)>player.getEntityInteractionRange())return false;
        return player.getWorld().raycast(new RaycastContext(from,target,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,player)).getType()==HitResult.Type.MISS;
    }
    public static double hit(PlayerEntity player,CorpseHistoryController.Entry entry){
        var from=player.getEyePos();var to=from.add(player.getRotationVec(1).multiply(player.getEntityInteractionRange()));
        var block=player.getWorld().raycast(new RaycastContext(from,to,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,player));
        if(block.getType()!=HitResult.Type.MISS)to=block.getPos();
        double best=Double.POSITIVE_INFINITY;
        if(entry.view!=null){
            if(!entry.view.dimension().equals(player.getWorld().getRegistryKey().getValue().toString()))return -1;
            var pose=entry.pose();
            if(pose==null){var frames=entry.session.frames();if(frames.isEmpty())return -1;pose=frames.getLast();}
            var origin=entry.view.origin();
            for(var part:pose.poses().entrySet()){
                var p=part.getValue();var inverse=p.rotation().conjugate();
                var a=from.subtract(origin).subtract(p.x(),p.y(),p.z());var b=to.subtract(origin).subtract(p.x(),p.y(),p.z());
                var x=inverse.transform(new Vector3f((float)a.x,(float)a.y,(float)a.z));var y=inverse.transform(new Vector3f((float)b.x,(float)b.y,(float)b.z));
                var size=StandardBodyPhysicsAdapter.size(part.getKey(),entry.view.slim()).mul(.5f*pose.scale());
                var box=new Box(-size.x,-size.y,-size.z,size.x,size.y,size.z);
                var localA=new Vec3d(x.x,x.y,x.z);var localB=new Vec3d(y.x,y.y,y.z);
                var hit=box.raycast(localA,localB);
                if(box.contains(localA))best=0;
                else if(hit.isPresent())best=Math.min(best,localA.distanceTo(hit.get()));
            }
        }else{
            ItemDisplayEntity display=null;
            // Only used on the authority thread for old corpses.
            if(player.getWorld() instanceof net.minecraft.server.world.ServerWorld world&&world.getEntity(entry.id) instanceof ItemDisplayEntity d)display=d;
            if(display!=null){var hit=CorpsePoseGeometry.pick(display,from,to);if(hit!=null)best=from.distanceTo(hit.position());}
        }
        return Double.isFinite(best)?best:-1;
    }
}
