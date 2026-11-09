package com.kuilunfuzhe.monvhua.features.forensics.blood;
import com.kuilunfuzhe.monvhua.features.forensics.ForensicConfig;
import com.kuilunfuzhe.monvhua.features.paint.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
/** Shared server projection. Paint coverage uses current paint, never private source IDs. */
public final class BloodVisibility {
    private BloodVisibility(){}
    private record Cached(BloodPatch source,long paintRevision,boolean coverage,BloodPatch projected){}
    private static final java.util.Map<ServerWorld,java.util.LinkedHashMap<String,Cached>> CACHE=new java.util.WeakHashMap<>();
    public static void reset(){CACHE.clear();}
    public static boolean painted(ServerWorld w,BloodPatch p,Vec3d hit){
        if(!ForensicConfig.current.cleaning.coverage)return false;
        var delta=hit.subtract(PaintSurface.point(p.pos(),p.face(),0,0,1,0));
        int x=MathHelper.clamp((int)(delta.dotProduct(p.right())*16),0,15),y=MathHelper.clamp((int)(delta.dotProduct(p.down())*16),0,15);
        return covered(PaintOverlayStore.get(w).getPixels(p.pos(),p.face()),x,y);
    }
    private static boolean covered(int[] paint,int x,int y){for(int dy=0;dy<4;dy++)for(int dx=0;dx<4;dx++)if((paint[(y*4+dy)*64+x*4+dx]>>>24)>0)return true;return false;}
    public static double density(BloodPatch p,Vec3d hit){var d=hit.subtract(PaintSurface.point(p.pos(),p.face(),0,0,1,0));return p.density(d.dotProduct(p.right()),d.dotProduct(p.down()));}
    public static BloodPatch project(ServerWorld w,BloodPatch p){
        var paints=PaintOverlayStore.get(w);long revision=paints.faceRevision(p.pos(),p.face());boolean coverage=ForensicConfig.current.cleaning.coverage;
        var cache=CACHE.computeIfAbsent(w,k->new java.util.LinkedHashMap<>(256,.75f,true));var old=cache.get(p.id());
        if(old!=null&&old.paintRevision==revision&&old.coverage==coverage&&old.source.equals(p))return old.projected;
        char[] mask=new char[256];int[] paint=coverage?paints.getPixels(p.pos(),p.face()):new int[4096];
        for(int y=0;y<16;y++)for(int x=0;x<16;x++){
            int alpha=p.density((x+.5)/16,(y+.5)/16);
            alpha=Math.max(28,alpha*245/255);
            boolean geometry=false;for(int dy=0;dy<4;dy++)for(int dx=0;dx<4;dx++)if(p.covers((x*4+dx+.5)/64,(y*4+dy+.5)/64))geometry=true;
            mask[y*16+x]=(char)(!geometry||covered(paint,x,y)?0:alpha);
        }
        var result=p.treated(new String(mask),p.revision());
        cache.put(p.id(),new Cached(p,revision,coverage,result));while(cache.size()>8192)cache.remove(cache.keySet().iterator().next());return result;
    }
}
