package com.kuilunfuzhe.monvhua.features.forensics;

import java.util.*;
import net.minecraft.util.math.*;

/** Separating-axis test for an affine box (corner bits x=1,y=2,z=4) against a world AABB. */
public final class OrientedBoxCollision {
    private OrientedBoxCollision(){}
    public static boolean overlaps(List<Vec3d> corners,Box box,double tolerance){
        if(corners.size()!=8||!Double.isFinite(tolerance)||tolerance<0)throw new IllegalArgumentException("Invalid collision input");
        var edges=List.of(corners.get(1).subtract(corners.getFirst()),corners.get(2).subtract(corners.getFirst()),corners.get(4).subtract(corners.getFirst()));
        var worldAxes=List.of(new Vec3d(1,0,0),new Vec3d(0,1,0),new Vec3d(0,0,1));
        var axes=new ArrayList<Vec3d>(15);axes.addAll(worldAxes);
        axes.add(edges.get(0).crossProduct(edges.get(1)));axes.add(edges.get(0).crossProduct(edges.get(2)));axes.add(edges.get(1).crossProduct(edges.get(2)));
        for(var edge:edges)for(var axis:worldAxes)axes.add(edge.crossProduct(axis));
        var center=box.getCenter();double hx=box.getLengthX()/2,hy=box.getLengthY()/2,hz=box.getLengthZ()/2;
        for(var raw:axes){
            if(raw.lengthSquared()<1e-18)continue;
            var axis=raw.normalize();double low=Double.POSITIVE_INFINITY,high=Double.NEGATIVE_INFINITY;
            for(var point:corners){double p=point.dotProduct(axis);low=Math.min(low,p);high=Math.max(high,p);}
            double mid=center.dotProduct(axis),radius=Math.abs(axis.x)*hx+Math.abs(axis.y)*hy+Math.abs(axis.z)*hz;
            // Directed separation distances also handle containment correctly.
            if(high-(mid-radius)<=tolerance||mid+radius-low<=tolerance)return false;
        }
        return true;
    }
}
