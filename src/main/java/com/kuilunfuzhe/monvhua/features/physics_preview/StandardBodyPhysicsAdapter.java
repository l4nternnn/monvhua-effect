package com.kuilunfuzhe.monvhua.features.physics_preview;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;

/** Matrices consume neutral model coordinates in blocks (ModelPart already divides pixels by 16). */
public final class StandardBodyPhysicsAdapter {
    private StandardBodyPhysicsAdapter(){}
    public static Vector3f center(String id) { return center(id,false); }
    public static Vector3f center(String id, boolean slim) {
        return switch(id){
            case "head" -> new Vector3f(0,-4,0);
            case "torso" -> new Vector3f(0,6,0);
            case "left_arm" -> new Vector3f(slim?5.5f:6,slim?6.5f:6,0);
            case "right_arm" -> new Vector3f(slim?-5.5f:-6,slim?6.5f:6,0);
            case "left_leg" -> new Vector3f(1.9f,18,0);
            case "right_leg" -> new Vector3f(-1.9f,18,0);
            default -> throw new IllegalArgumentException("Unknown part "+id);
        };
    }
    public static Matrix4f matrix(String id,PhysicsPoseDocument.Pose p) {
        return matrix(id,p,false);
    }
    public static Matrix4f matrix(String id,PhysicsPoseDocument.Pose p,boolean slim) {
        return matrix(id,p,slim,1);
    }
    public static Matrix4f matrix(String id,PhysicsPoseDocument.Pose p,boolean slim,float scale) {
        CorpseScale.require(scale);
        var c=center(id,slim).div(16);
        return new Matrix4f().translate(p.x(),p.y(),p.z()).rotate(p.rotation()).rotateX((float)Math.PI).scale(scale).translate(-c.x,-c.y,-c.z);
    }
    public static Map<String,Matrix4f> matrices(PhysicsPoseDocument d) {
        return matrices(d,false);
    }
    public static Map<String,Matrix4f> matrices(PhysicsPoseDocument d,boolean slim) {
        Map<String,Matrix4f> result=new LinkedHashMap<>();
        for(String id:d.poses().keySet())result.put(id,matrix(id,d.poses().get(id),slim,d.scale()));
        return Collections.unmodifiableMap(result);
    }
    public static Vector3f size(String id,boolean slim){var size=size(id);if(slim&&id.endsWith("arm"))size.x=3f/16;return size;}
    public static Vector3f size(String id){return id.equals("head")?new Vector3f(.5f,.5f,.5f):new Vector3f(id.equals("torso")?.5f:.25f,.75f,.25f);}
}
