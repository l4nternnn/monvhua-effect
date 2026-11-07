package com.kuilunfuzhe.monvhua.features.forensics.viewport;

import net.minecraft.util.math.Box;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** Independent orbit camera. World axes stay intact; the same projection drives the callouts. */
public final class EvidenceViewportFrame {
    private final Matrix4f projection, view, combined;
    private final Vector3f eye;
    private final int left, top, width, height;
    public EvidenceViewportFrame(int left,int top,int width,int height,Box bounds,float yaw,float pitch) {
        this.left=left;this.top=top;this.width=Math.max(1,width);this.height=Math.max(1,height);
        Vector3f center=new Vector3f((float)((bounds.minX+bounds.maxX)/2),(float)((bounds.minY+bounds.maxY)/2),(float)((bounds.minZ+bounds.maxZ)/2));
        float radius=(float)Math.max(.1,Math.sqrt(bounds.getLengthX()*bounds.getLengthX()+bounds.getLengthY()*bounds.getLengthY()+bounds.getLengthZ()*bounds.getLengthZ())/2);
        float aspect=(float)this.width/this.height, half=(float)Math.toRadians(20);
        float distance=radius/(float)Math.sin(Math.min(half,Math.atan(Math.tan(half)*aspect)))*1.12f;
        float y=(float)Math.toRadians(yaw),p=(float)Math.toRadians(pitch);
        eye=new Vector3f((float)(Math.sin(y)*Math.cos(p)),(float)Math.sin(p),(float)(Math.cos(y)*Math.cos(p))).mul(distance).add(center);
        view=new Matrix4f().lookAt(eye,center,new Vector3f(0,1,0));
        projection=new Matrix4f().perspective(half*2,aspect,Math.max(.001f,distance-radius*1.5f),distance+radius*2);
        combined=new Matrix4f(projection).mul(view);
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
