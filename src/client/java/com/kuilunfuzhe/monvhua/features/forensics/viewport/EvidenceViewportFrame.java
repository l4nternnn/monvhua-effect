package com.kuilunfuzhe.monvhua.features.forensics.viewport;

import net.minecraft.util.math.Box;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** Independent orbit camera. World axes stay intact; the same projection drives the callouts. */
public final class EvidenceViewportFrame {
    private final Matrix4f projection, view, combined;
    private final Vector3f eye, center;
    private final org.joml.Quaternionf orientation;
    private final float unitsPerPixel;
    private final int left, top, width, height;
    public EvidenceViewportFrame(int left,int top,int width,int height,Box bounds,float yaw,float pitch) {
        this(left,top,width,height,bounds,yaw,pitch,null,1);
    }
    public EvidenceViewportFrame(int left,int top,int width,int height,Box bounds,float yaw,float pitch,Vector3f target,float zoom) {
        this(left,top,width,height,bounds,orientation(yaw,pitch),target,zoom);
    }
    public static org.joml.Quaternionf orientation(float yaw,float pitch) {
        return new org.joml.Quaternionf().rotateY((float)Math.toRadians(yaw)).rotateX((float)Math.toRadians(-pitch));
    }
    /** Local camera axes: visible front surfaces follow the screen-space mouse delta. */
    public static org.joml.Quaternionf drag(org.joml.Quaternionf current,float dx,float dy) {
        return new org.joml.Quaternionf(current)
                .rotateY((float)Math.toRadians(-dx*.5f))
                .rotateX((float)Math.toRadians(-dy*.5f)).normalize();
    }
    /** Positive angles turn the displayed scene clockwise, around the viewing axis. */
    public static org.joml.Quaternionf roll(org.joml.Quaternionf current,float degrees) {
        return new org.joml.Quaternionf(current).rotateZ((float)Math.toRadians(degrees)).normalize();
    }
    public EvidenceViewportFrame(int left,int top,int width,int height,Box bounds,org.joml.Quaternionf orientation,Vector3f target,float zoom) {
        this.left=left;this.top=top;this.width=Math.max(1,width);this.height=Math.max(1,height);
        this.orientation=new org.joml.Quaternionf(orientation);
        center=new Vector3f((float)((bounds.minX+bounds.maxX)/2),(float)((bounds.minY+bounds.maxY)/2),(float)((bounds.minZ+bounds.maxZ)/2));
        if(target!=null)center.set(target);
        float radius=(float)Math.max(.1,Math.sqrt(bounds.getLengthX()*bounds.getLengthX()+bounds.getLengthY()*bounds.getLengthY()+bounds.getLengthZ()*bounds.getLengthZ())/2);
        float aspect=(float)this.width/this.height, half=(float)Math.toRadians(20);
        float distance=radius/(float)Math.sin(Math.min(half,Math.atan(Math.tan(half)*aspect)))*1.12f;
        distance*=Math.max(.025f,Math.min(4,zoom));
        unitsPerPixel=2*distance*(float)Math.tan(half)/this.height;
        eye=orientation.transform(new Vector3f(0,0,distance)).add(center);
        Vector3f up=orientation.transform(new Vector3f(0,1,0));
        view=new Matrix4f().lookAt(eye,center,up);
        projection=new Matrix4f().perspective(half*2,aspect,.005f,distance+radius*4);
        combined=new Matrix4f(projection).mul(view);
    }
    /** Translate the camera opposite the drag, so the scene follows the pointer at target depth. */
    public Vector3f pannedTarget(float dx,float dy) {
        return orientation.transform(new Vector3f(-dx*unitsPerPixel,dy*unitsPerPixel,0)).add(center);
    }
    public Matrix4f projection(){return new Matrix4f(projection);}
    public Matrix4f view(){return new Matrix4f(view);}
    public Vector3f eye(){return new Vector3f(eye);}
    public Point project(Vector3f point){
        Vector4f p=combined.transform(new Vector4f(point,1));
        if(!Float.isFinite(p.w)||p.w<=0||Math.abs(p.z)>p.w||Math.abs(p.x)>p.w||Math.abs(p.y)>p.w)return null;
        return new Point(left+(p.x/p.w+1)*width/2,top+(1-p.y/p.w)*height/2);
    }
    public record Point(float x,float y){}
}
