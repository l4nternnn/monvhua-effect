package com.kuilunfuzhe.monvhua.features.forensics.cards;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.kuilunfuzhe.monvhua.features.forensics.viewport.*;
import com.kuilunfuzhe.monvhua.mixin.BodyPoseDrawContextAccessor;
import net.minecraft.client.gui.*;
import net.minecraft.nbt.NbtCompound;
import org.joml.Matrix3x2f;
import org.joml.Vector3f;
import java.util.*;

/** Reconstructs saved blocks using a locked camera. Does not read live chunks or capture pixels. */
final class ClueCardEnvironmentPreview {
    private record Model(String source,EvidenceBlocks blocks,Vector3f eye,float fov){}
    private static final Map<String,Model> MODELS=new LinkedHashMap<>(16,.75f,true);
    static void clear(){MODELS.clear();}
    static boolean render(DrawContext c,ClueCardPackets.Detail detail,int x,int y,int width,int height){
        if(detail.scene()==null||detail.scene().isEmpty())return false;
        try{
            var model=MODELS.get(detail.id());
            if(model==null||!model.source().equals(detail.scene())){
                var n=NbtCompound.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseString(detail.scene())).getOrThrow();
                var eye=new Vector3f((float)n.getDouble("eye_x",0),(float)n.getDouble("eye_y",0),(float)n.getDouble("eye_z",0));
                double tangent=n.getDouble("tangent",1);
                if(!eye.isFinite()||eye.lengthSquared()<.001||!Double.isFinite(tangent)||tangent<=0||tangent>2)return false;
                var blocks=EvidenceBlocks.read(n);if(blocks==null)return false;
                model=new Model(detail.scene(),blocks,eye,(float)(2*Math.atan(tangent*.75)));MODELS.put(detail.id(),model);
                while(MODELS.size()>24)MODELS.remove(MODELS.keySet().iterator().next());
            }
            int w=Math.min(width,height*4/3),h=w*3/4;if(w<1||h<1)return true;x+=(width-w)/2;y+=(height-h)/2;
            var frame=EvidenceViewportFrame.fixed(x,y,w,h,model.eye(),model.fov());
            var pose=new Matrix3x2f(c.getMatrices());var clip=new ScreenRect(x,y,w,h).transformEachVertex(pose);
            var blocks=model.blocks();
            ((BodyPoseDrawContextAccessor)c).monvhua$getPoseGuiState().addSpecialElement(new EvidenceViewportRenderState(x,y,x+w,y+h,pose,clip,frame,blocks::render));
            return true;
        }catch(IllegalArgumentException|IllegalStateException error){return false;}
    }
}
