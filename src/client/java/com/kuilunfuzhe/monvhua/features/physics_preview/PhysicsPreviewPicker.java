package com.kuilunfuzhe.monvhua.features.physics_preview;

import com.kuilunfuzhe.monvhua.renderer.body.special.CombinedBodySpecialModelRenderer.EvidenceSurface;
import net.minecraft.util.math.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.List;

public final class PhysicsPreviewPicker {
    private PhysicsPreviewPicker(){}
    public record Hit(String surface,Direction face,double u,double v,double distance,Vec3d point){}
    public static Hit pick(List<EvidenceSurface> surfaces,Vec3d from,Vec3d to) {
        Hit best=null;
        for(var surface:surfaces) {
            var inverse=new Matrix4f(surface.transform()).invert();
            var a=inverse.transformPosition(new Vector3f((float)from.x,(float)from.y,(float)from.z));
            var b=inverse.transformPosition(new Vector3f((float)to.x,(float)to.y,(float)to.z));
            var intersection=surface.box().raycast(new Vec3d(a.x,a.y,a.z),new Vec3d(b.x,b.y,b.z));
            if(intersection.isEmpty())continue;
            var local=intersection.get();var box=surface.box();
            var world=surface.transform().transformPosition(new Vector3f((float)local.x,(float)local.y,(float)local.z));
            var point=new Vec3d(world.x,world.y,world.z);double distance=from.distanceTo(point);
            if(best!=null&&distance>=best.distance()-1e-6)continue;
            double[] gaps={Math.abs(local.y-box.minY),Math.abs(local.y-box.maxY),Math.abs(local.z-box.minZ),Math.abs(local.z-box.maxZ),Math.abs(local.x-box.minX),Math.abs(local.x-box.maxX)};
            Direction[] faces={Direction.UP,Direction.DOWN,Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST};
            int face=0;for(int i=1;i<6;i++)if(gaps[i]<gaps[face])face=i;
            double u=face>=4?(local.z-box.minZ)/box.getLengthZ():(local.x-box.minX)/box.getLengthX();
            double v=face<2?(local.z-box.minZ)/box.getLengthZ():(local.y-box.minY)/box.getLengthY();
            best=new Hit(surface.name(),faces[face],MathHelper.clamp(u,0,1),MathHelper.clamp(v,0,1),distance,point);
        }
        return best;
    }
}
