package com.kuilunfuzhe.monvhua.features.forensics.cards;

import com.google.gson.JsonParser;
import com.kuilunfuzhe.monvhua.features.forensics.viewport.*;
import com.kuilunfuzhe.monvhua.mixin.BodyPoseDrawContextAccessor;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.gui.*;
import net.minecraft.nbt.NbtCompound;
import org.joml.Matrix3x2f;
import java.util.*;

/** One filtered wound per immutable child card, reusing the existing model viewport. */
final class ClueCardBodyPreview {
    private record Model(EvidenceScene scene,NbtCompound wound){}
    private static final Map<String,Model> MODELS=new HashMap<>();
    static void clear(){MODELS.clear();}
    static void render(DrawContext context,ClueCardPackets.Detail detail,int x,int y,int width,int height){
        if(width<1||height<1)return;
        try{
            var model=MODELS.computeIfAbsent(detail.id(),id->{var n=NbtCompound.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseString(detail.body())).getOrThrow();return new Model(new EvidenceScene(n.getCompoundOrEmpty("observation"),n.getListOrEmpty("wounds")),n.getListOrEmpty("wounds").getCompound(0).orElse(new NbtCompound()));});
            var prepared=model.scene.prepare();var target=prepared.target(model.wound);float yaw=180,pitch=25,zoom=1;org.joml.Vector3f position=null;
            if(target!=null){yaw=(float)Math.toDegrees(Math.atan2(target.normal().x,target.normal().z));pitch=(float)Math.toDegrees(Math.asin(Math.max(-1,Math.min(1,target.normal().y))));position=target.position();zoom=.35f;}
            var frame=new EvidenceViewportFrame(x,y,width,height,prepared.bounds(),yaw,pitch,position,zoom);
            var pose=new Matrix3x2f(context.getMatrices());var clip=new ScreenRect(x,y,width,height).transformEachVertex(pose);
            ((BodyPoseDrawContextAccessor)context).monvhua$getPoseGuiState().addSpecialElement(new EvidenceViewportRenderState(x,y,x+width,y+height,pose,clip,frame,prepared.render()));
        }catch(IllegalArgumentException|IllegalStateException e){/* A corrupt card stays a placeholder; never substitute live scene data. */}
    }
}
