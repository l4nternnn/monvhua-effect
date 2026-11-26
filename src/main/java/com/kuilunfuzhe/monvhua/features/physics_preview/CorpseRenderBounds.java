package com.kuilunfuzhe.monvhua.features.physics_preview;

import com.kuilunfuzhe.monvhua.features.forensics.physics.CorpsePhysicsBridge;
import net.minecraft.util.math.Box;
import java.util.List;

/** Conservative local bounds for world rendering only. Includes the whole interpolation window. */
public final class CorpseRenderBounds {
    private CorpseRenderBounds() {}
    public static Box of(List<CorpsePhysicsBridge.TimedPose> frames, boolean slim) {
        if(frames.isEmpty())return null;
        double minX=Double.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
        for(var frame:frames)for(var entry:frame.pose().poses().entrySet()){
            var pose=entry.getValue();var size=StandardBodyPhysicsAdapter.size(entry.getKey(),slim);
            // Circumscribed sphere also covers intermediate quaternion rotations between frames.
            double radius=size.length()*.5*frame.pose().scale()+.15*frame.pose().scale();
            if(!Double.isFinite(radius)||!Float.isFinite(pose.x())||!Float.isFinite(pose.y())||!Float.isFinite(pose.z()))return null;
            minX=Math.min(minX,pose.x()-radius);minY=Math.min(minY,pose.y()-radius);minZ=Math.min(minZ,pose.z()-radius);
            maxX=Math.max(maxX,pose.x()+radius);maxY=Math.max(maxY,pose.y()+radius);maxZ=Math.max(maxZ,pose.z()+radius);
        }
        return minX==Double.POSITIVE_INFINITY?null:new Box(minX,minY,minZ,maxX,maxY,maxZ);
    }
}
