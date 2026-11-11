package com.kuilunfuzhe.monvhua.features.forensics.cards;

import net.minecraft.util.math.Vec3d;

/** The confirmation dialog must not depend on the user's later crouch state or aim. */
public record ClueCaptureTarget(Vec3d point,Vec3d eye,long expires,String dimension) {
    public void validate(Vec3d currentEye,String currentDimension,long now){
        if(!dimension.equals(currentDimension))throw new IllegalArgumentException("已离开记录点所在维度，请重新选点");
        if(now>expires)throw new IllegalArgumentException("记录点确认已过期，请返回重新选点");
        if(currentEye.squaredDistanceTo(eye)>1||currentEye.squaredDistanceTo(point)>4.5*4.5)
            throw new IllegalArgumentException("已离开记录位置，请返回原位重试或重新选点");
    }
}
