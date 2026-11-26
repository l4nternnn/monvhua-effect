package com.kuilunfuzhe.monvhua.features.forensics.butterfly;

import com.kuilunfuzhe.monvhua.features.forensics.blood.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import net.minecraft.block.ShapeContext;
import java.util.*;

/** Visual butterfly residue is not an attack, a victim identity, or a new breeding source. */
public final class ButterflyResidue {
    public static final String EVENT_PREFIX="butterfly-expiry/";
    public static final double SEARCH_RADIUS=4;
    private ButterflyResidue() {}
    public static boolean isBirthSource(BloodTraceStore.Entry entry){return !entry.event().startsWith(EVENT_PREFIX);}
    private record Candidate(BlockPos block,Direction face,Vec3d point,double distance) {}

    /** First visible face among the closest 32 loaded, paintable full-block faces within four blocks. */
    public static BlockHitResult nearest(ServerWorld world,Vec3d from){
        if(!ButterflyClearance.finite(from)||!world.isChunkLoaded(BlockPos.ofFloored(from)))return null;
        var candidates=new ArrayList<Candidate>();var center=BlockPos.ofFloored(from);
        for(var mutable:BlockPos.iterate(center.add(-4,-4,-4),center.add(4,4,4))){
            BlockPos block=mutable.toImmutable();
            if(block.getY()<world.getBottomY()||block.getY()>world.getTopYInclusive()
                    ||!world.getWorldBorder().contains(block)||!loadedSegment(world,Vec3d.ofCenter(block),Vec3d.ofCenter(block))
                    ||!BloodSurfaceProjection.supported(world,block))continue;
            for(var face:Direction.values()){
                Vec3d point=nearestPoint(from,block,face);
                double distance=from.squaredDistanceTo(point);
                if(distance<=SEARCH_RADIUS*SEARCH_RADIUS&&from.subtract(point).dotProduct(Vec3d.of(face.getVector()))>0)
                    candidates.add(new Candidate(block,face,point,distance));
            }
        }
        candidates.sort(Comparator.comparingDouble(Candidate::distance));
        // Candidate distances are exact distances to full block faces, not a six-ray approximation.
        for(int i=0;i<Math.min(32,candidates.size());i++){
            var c=candidates.get(i);Vec3d end=c.point.subtract(Vec3d.of(c.face.getVector()).multiply(.01));
            if(!loadedSegment(world,from,end))continue;
            var hit=world.raycast(new RaycastContext(from,end,RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.ANY,ShapeContext.absent()));
            if(hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(c.block)&&hit.getSide()==c.face)return hit;
        }
        return null;
    }
    private static boolean loadedSegment(ServerWorld world,Vec3d from,Vec3d to){
        int steps=Math.max(1,(int)Math.ceil(from.distanceTo(to)/.2));
        for(int i=0;i<=steps;i++){
            var p=BlockPos.ofFloored(from.lerp(to,(double)i/steps));
            // Raycast shape providers may consult a neighbouring block.
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)if(!world.isChunkLoaded(p.add(dx,0,dz)))return false;
        }
        return true;
    }
    public static Vec3d nearestPoint(Vec3d from,BlockPos block,Direction face){
        double x=MathHelper.clamp(from.x,block.getX()+.001,block.getX()+.999);
        double y=MathHelper.clamp(from.y,block.getY()+.001,block.getY()+.999);
        double z=MathHelper.clamp(from.z,block.getZ()+.001,block.getZ()+.999);
        return switch(face){
            case UP->new Vec3d(x,block.getY()+1,z);
            case DOWN->new Vec3d(x,block.getY(),z);
            case NORTH->new Vec3d(x,y,block.getZ());
            case SOUTH->new Vec3d(x,y,block.getZ()+1);
            case WEST->new Vec3d(block.getX(),y,z);
            case EAST->new Vec3d(block.getX()+1,y,z);
        };
    }
    public static boolean deposit(ServerWorld world,Vec3d from,long seed){
        if(!BloodRules.generating())return false;
        var hit=nearest(world,from);if(hit==null)return false;
        var patch=BloodSurfaceProjection.impact(hit,.075f,seed);
        // Persist a distinct event marker; never claim an original wound's blood source.
        return BloodTraceService.submit(world,new BloodTraceStore.Entry(patch,"",EVENT_PREFIX+UUID.randomUUID(),
                world.getBlockState(patch.pos()).toString(),false));
    }
}
