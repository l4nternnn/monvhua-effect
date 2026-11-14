package com.kuilunfuzhe.monvhua.features.forensics;

import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.*;

/** Server geometry uses the same neutral mesh boxes and rigid transforms as rendering. */
public final class CorpsePoseGeometry {
    private CorpsePoseGeometry() {}
    public record Surface(String name,String part,Box box,Matrix4f matrix) {}
    public record Hit(String part,String surface,Vec3d position) {}
    public record WoundHit(Vec3d position, NbtCompound wound) {}
    /** UVs are computed after undoing the actual posed surface transform, never in standing-player space. */
    public static WoundHit woundHit(List<Surface> surfaces, Vec3d anchor, Vec3d from, Vec3d to) {
        WoundHit best = null; double distance = Double.POSITIVE_INFINITY;
        for (Surface s : surfaces) {
            float determinant = s.matrix().determinant();
            if (!Float.isFinite(determinant) || Math.abs(determinant) < 1e-8) continue;
            Matrix4f inverse = new Matrix4f(s.matrix()).invert();
            Vec3d a = transform(inverse, from.subtract(anchor)), b = transform(inverse, to.subtract(anchor));
            if (s.box().contains(a)) continue; // An eye inside a model is not a reliable external surface strike.
            var local = s.box().raycast(a, b);
            if (local.isEmpty()) continue;
            Vec3d world = transform(s.matrix(), local.get()).add(anchor);
            double d = world.squaredDistanceTo(from);
            if (Double.isFinite(d) && d < distance) {
                distance = d;
                best = new WoundHit(world, WoundGeometry.describe(new WoundGeometry.Surface(s.name(), s.part(), s.box()), local.get()));
            }
        }
        return best;
    }
    public static List<Surface> surfaces(ItemDisplayEntity display,NbtCompound data) {
        Matrix4f root=CorpseGeometrySync.placement(display,data);
        return WoundGeometry.surfaces(data).stream().map(s->new Surface(s.name(),s.part(),
                new Box(s.box().minX/16,s.box().minY/16,s.box().minZ/16,s.box().maxX/16,s.box().maxY/16,s.box().maxZ/16),
                new Matrix4f(root).mul(CorpseSurfacePose.transform(s,data)))).toList();
    }
    public static List<Vec3d> corners(Surface s) {
        List<Vec3d> points=new ArrayList<>();Box b=s.box();
        for(int i=0;i<8;i++) {
            Vector3f p=s.matrix().transformPosition(new Vector3f((float)((i&1)==0?b.minX:b.maxX),
                    (float)((i&2)==0?b.minY:b.maxY),(float)((i&4)==0?b.minZ:b.maxZ)));
            points.add(new Vec3d(p.x,p.y,p.z));
        }
        return points;
    }
    public static Box bounds(Surface s) {
        Box b=null;for(Vec3d p:corners(s)){Box point=new Box(p,p);b=b==null?point:b.union(point);}return b;
    }
    /** AABB broad phase followed by oriented surface/terrain SAT; contact tolerance stays 0.03 blocks. */
    public static boolean clear(ServerWorld world,ItemDisplayEntity display,NbtCompound data,Vec3d anchor) {
        for(var s:surfaces(display,data)) {
            Box b=bounds(s).offset(anchor);
            var points=corners(s).stream().map(p->p.add(anchor)).toList();
            for(int x=MathHelper.floor(b.minX)>>4;x<=MathHelper.floor(b.maxX)>>4;x++)
                for(int z=MathHelper.floor(b.minZ)>>4;z<=MathHelper.floor(b.maxZ)>>4;z++)
                    if(!world.isChunkLoaded(new BlockPos(x<<4,MathHelper.floor(anchor.y),z<<4)))return false;
            for(var shape:world.getBlockCollisions(display,b))for(var obstacle:shape.getBoundingBoxes())
                if(OrientedBoxCollision.overlaps(points,obstacle,.03))return false;
        }
        return true;
    }
    public static boolean supported(ServerWorld world,ItemDisplayEntity display,NbtCompound data,Vec3d anchor) {
        for(var s:surfaces(display,data)) for(Vec3d p:corners(s)) {
            p=p.add(anchor);
            var hit=world.raycast(new net.minecraft.world.RaycastContext(p.add(0,.04,0),p.add(0,-.06,0),
                    net.minecraft.world.RaycastContext.ShapeType.COLLIDER,net.minecraft.world.RaycastContext.FluidHandling.NONE,display));
            if(hit.getType()==net.minecraft.util.hit.HitResult.Type.BLOCK&&hit.getSide()==Direction.UP)return true;
        }
        return false;
    }
    /** Root-part proxy for existing pool/drip rules; this is not per-wound bleeding. */
    public static Vec3d bloodOrigin(ItemDisplayEntity display) {
        var data=CorpsePoseRecord.data(display.getItemStack());
        if(CorpsePoseRecord.read(data).isEmpty())return display.getPos();
        var pose=CorpsePoseRecord.read(data).orElseThrow();var center=pose.parts().get(pose.rootPart());return display.getPos().add(center.x(),center.y(),center.z());
    }
    public static Hit pick(ItemDisplayEntity display,Vec3d from,Vec3d to) {
        Hit best=null;double distance=Double.POSITIVE_INFINITY;
        for(var s:surfaces(display,CorpsePoseRecord.data(display.getItemStack()))) {
            Matrix4f inverse=new Matrix4f(s.matrix()).invert();
            Vec3d a=transform(inverse,from.subtract(display.getPos())),b=transform(inverse,to.subtract(display.getPos()));
            var hit=s.box().raycast(a,b);
            if(hit.isEmpty())continue;
            Vec3d world=transform(s.matrix(),hit.get()).add(display.getPos());double d=world.squaredDistanceTo(from);
            if(d<distance){distance=d;best=new Hit(s.part(),s.name(),world);}
        }
        return best;
    }
    private static Vec3d transform(Matrix4f m,Vec3d p){var v=m.transformPosition(new Vector3f((float)p.x,(float)p.y,(float)p.z));return new Vec3d(v.x,v.y,v.z);}
}
