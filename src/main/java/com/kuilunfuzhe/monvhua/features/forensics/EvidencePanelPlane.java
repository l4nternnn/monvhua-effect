package com.kuilunfuzhe.monvhua.features.forensics;

import org.joml.Quaterniond;
import org.joml.Quaternionfc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Shared world-panel coordinates: +X right, +Y up, +Z front. */
public final class EvidencePanelPlane {
    private final Vector3d center;
    private final Quaterniond rotation;
    private final double width,height;
    public EvidencePanelPlane(Vector3dc center,Quaternionfc rotation,double width,double height){
        if(!(width>0&&height>0&&Double.isFinite(width)&&Double.isFinite(height)))throw new IllegalArgumentException("Invalid panel size");
        this.center=new Vector3d(center);this.rotation=new Quaterniond(rotation).normalize();this.width=width;this.height=height;
    }
    public Vector3d world(double u,double v){return rotation.transform(new Vector3d((u-.5)*width,(.5-v)*height,0)).add(center);}
    public Hit intersect(Vector3dc eye,Vector3dc direction,double maxDistance){
        var inverse=new Quaterniond(rotation).conjugate();
        var origin=inverse.transform(new Vector3d(eye).sub(center));
        var ray=new Vector3d(direction);
        if(ray.lengthSquared()<1e-12)return null;
        inverse.transform(ray.normalize());
        if(origin.z<=0||ray.z>=-1e-8)return null;
        double t=-origin.z/ray.z;
        if(t<0||t>maxDistance)return null;
        double u=(origin.x+t*ray.x)/width+.5,v=.5-(origin.y+t*ray.y)/height;
        if(u<0||u>1||v<0||v>1)return null;
        return new Hit(u,v,t);
    }
    public record Hit(double u,double v,double distance){}
}
