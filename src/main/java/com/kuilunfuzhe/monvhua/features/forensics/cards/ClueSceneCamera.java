package com.kuilunfuzhe.monvhua.features.forensics.cards;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Fixed camera math shared by the constructed scene and its regression checks. */
public final class ClueSceneCamera {
    private ClueSceneCamera(){}
    public static Matrix4f view(Vector3f position){
        if(!position.isFinite()||position.lengthSquared()<.0001f)throw new IllegalArgumentException("Invalid scene camera");
        var eye=new Vector3f(position);var forward=new Vector3f(eye).negate().normalize();
        var right=new Vector3f(forward).cross(0,1,0);if(right.lengthSquared()<.001f)right.set(1,0,0);right.normalize();
        var up=new Vector3f(right).cross(forward).normalize();
        return new Matrix4f().lookAt(eye,new Vector3f(),up);
    }
    public static Matrix4f projection(float verticalFov){
        if(!Float.isFinite(verticalFov)||verticalFov<=0||verticalFov>=Math.PI)throw new IllegalArgumentException("Invalid scene projection");
        return new Matrix4f().perspective(verticalFov,4f/3,.005f,32);
    }
}
