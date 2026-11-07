package com.kuilunfuzhe.monvhua.features.forensics;

import net.minecraft.util.math.*;
import net.minecraft.nbt.*;
import java.util.*;

/** Neutral Steve geometry, in model pixels; +Y down, -Z front. */
public final class WoundGeometry {
    public record Surface(String name, String part, Box box) {}
    public static final List<Surface> SURFACES = List.of(
        s("head","head",-4,-8,-4,4,0,4),
        s("body_upper","torso",-4,0,-2,4,6,2), s("body_lower","torso",-4,6,-2,4,12,2),
        s("right_arm_upper","right_arm",-8,0,-2,-4,6,2), s("right_arm_lower","right_arm",-8,6,-2,-4,12,2),
        s("left_arm_upper","left_arm",4,0,-2,8,6,2), s("left_arm_lower","left_arm",4,6,-2,8,12,2),
        s("right_leg_upper","right_leg",-3.9,12,-2,.1,18,2), s("right_leg_lower","right_leg",-3.9,18,-2,.1,24,2),
        s("left_leg_upper","left_leg",-.1,12,-2,3.9,18,2), s("left_leg_lower","left_leg",-.1,18,-2,3.9,24,2));
    private static Surface s(String name,String part,double a,double b,double c,double d,double e,double f) { return new Surface(name,part,new Box(a,b,c,d,e,f)); }
    public static NbtCompound hit(Vec3d from, Vec3d to) {
        Surface best=null; Vec3d point=null; double distance=Double.MAX_VALUE;
        for (Surface surface:SURFACES) {
            var p=surface.box.raycast(from,to);
            if(p.isPresent() && p.get().squaredDistanceTo(from)<distance) { best=surface;point=p.get();distance=point.squaredDistanceTo(from); }
        }
        NbtCompound result=new NbtCompound(); if(best==null) return result;
        Box b=best.box; double[] distances={Math.abs(point.y-b.minY),Math.abs(point.y-b.maxY),Math.abs(point.z-b.minZ),Math.abs(point.z-b.maxZ),Math.abs(point.x-b.minX),Math.abs(point.x-b.maxX)};
        int face=0; for(int i=1;i<6;i++) if(distances[i]<distances[face])face=i;
        String[] faces={"UP","DOWN","NORTH","SOUTH","WEST","EAST"};
        double u=(face>=4?(point.z-b.minZ)/(b.maxZ-b.minZ):(point.x-b.minX)/(b.maxX-b.minX));
        double v=(face<2?(point.z-b.minZ)/(b.maxZ-b.minZ):(point.y-b.minY)/(b.maxY-b.minY));
        result.putString("surface",best.name);result.putString("part",best.part);result.putString("face",faces[face]);
        result.putDouble("u",MathHelper.clamp(u,0,1));result.putDouble("v",MathHelper.clamp(v,0,1));return result;
    }
    public static Vec3d local(Vec3d world, Vec3d feet, float yaw, float scale) {
        Vec3d p=world.subtract(feet);double r=Math.toRadians(yaw), k=16.0/scale;
        return new Vec3d((p.x*Math.cos(r)+p.z*Math.sin(r))*k,24-p.y*k,(p.x*Math.sin(r)-p.z*Math.cos(r))*k);
    }
}
