package com.kuilunfuzhe.monvhua.features.forensics.cards;

import com.kuilunfuzhe.monvhua.features.forensics.blood.BloodPatch;
import com.kuilunfuzhe.monvhua.features.forensics.viewport.*;
import com.kuilunfuzhe.monvhua.features.paint.*;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.nbt.*;
import net.minecraft.util.math.*;
import java.util.*;

/** Immutable composition of saved blocks, blood geometry and body appearances. */
final class ClueScenePreview implements EvidenceEnvironmentLayer {
    private record Stain(BloodPatch patch,Vec3d offset,List<float[]> spans){}
    private final EvidenceBlocks blocks;
    private final List<EvidenceScene> bodies=new ArrayList<>();
    private final List<Stain> blood=new ArrayList<>();
    ClueScenePreview(NbtCompound scene){
        blocks=EvidenceBlocks.read(scene);
        for(var e:scene.getListOrEmpty("bodies"))if(e instanceof NbtCompound body&&bodies.size()<8)
            bodies.add(new EvidenceScene(body.getCompoundOrEmpty("observation"),body.getListOrEmpty("wounds"),null));
        for(var e:scene.getListOrEmpty("blood"))if(e instanceof NbtCompound n&&blood.size()<128){
            var patch=BloodPatch.read(n);var offset=new Vec3d(n.getDouble("x",0),n.getDouble("y",0),n.getDouble("z",0));
            if(!Double.isFinite(offset.lengthSquared())||offset.lengthSquared()>25)continue;
            var spans=new ArrayList<float[]>();
            for(int y=0;y<32;y++){int start=0,alpha=0;for(int x=0;x<=32;x++){
                double u=(x+.5)/32,v=(y+.5)/32;int next=x<32&&patch.covers(u,v)?patch.density(u,v):0;
                if(next!=alpha){if(alpha>0)spans.add(new float[]{start/32f,y/32f,x/32f,(y+1)/32f,alpha});start=x;alpha=next;}
            }}
            blood.add(new Stain(patch,offset,List.copyOf(spans)));
        }
    }
    @Override public long revision(){return blocks==null?0:blocks.revision();}
    @Override public Box bounds(){return blocks==null?new Box(-1,-1,-1,1,1,1):blocks.bounds();}
    @Override public void render(MatrixStack matrices,VertexConsumerProvider.Immediate consumers){
        if(blocks!=null)blocks.render(matrices,consumers);
        for(var body:bodies)body.prepare().render().render(matrices,consumers);
        var out=consumers.getBuffer(PaintRenderLayers.paintOverlay());var matrix=matrices.peek().getPositionMatrix();
        for(var stain:blood){var p=stain.patch();var origin=PaintSurface.point(BlockPos.ORIGIN,p.face(),0,0,1,.006).add(stain.offset());var normal=PaintSurface.normal(p.face());
            for(var s:stain.spans()){
                for(int corner=0;corner<4;corner++){
                    double u=corner==0||corner==3?s[0]:s[2],v=corner<2?s[1]:s[3];var point=origin.add(p.right().multiply(u)).add(p.down().multiply(v));
                    out.vertex(matrix,(float)point.x,(float)point.y,(float)point.z).color(95+(int)(p.seed()&15),5,12,(int)s[4]).texture(0,0).overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal((float)normal.x,(float)normal.y,(float)normal.z);
                }
            }
        }
    }
}
