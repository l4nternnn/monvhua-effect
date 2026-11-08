package com.kuilunfuzhe.monvhua.features.physics_preview;

import com.kuilunfuzhe.monvhua.features.paint.ModelPaintData;
import com.kuilunfuzhe.monvhua.features.forensics.WoundGeometry;
import net.minecraft.nbt.*;
import net.minecraft.util.math.Direction;

public final class PhysicsPreviewFixtures {
    private PhysicsPreviewFixtures(){}
    public static NbtCompound marks() {
        NbtCompound root=new NbtCompound();NbtList wounds=new NbtList();
        for(var surface:WoundGeometry.SURFACES)for(Direction face:Direction.values()) {
            var size=ModelPaintData.size(surface.name(),face,false);
            int[] pixels=new int[size.width()*size.height()];
            // Asymmetric L marker separate from the wound stamp.
            for(int y=0;y<Math.min(3,size.height());y++)pixels[y*size.width()]=0xFF00FFFF;
            if(size.width()>1)pixels[1]=surface.part().startsWith("left")?0xFFFF00FF:0xFFFFFF00;
            ModelPaintData.setFacePixels(root,surface.name(),face,pixels,false);
            int[] outer=new int[pixels.length];outer[outer.length-1]=0xFFFF80FF;
            ModelPaintData.setFacePixels(root,surface.name()+"_outer",face,outer,false);
            var wound=new NbtCompound();wound.putString("surface",surface.name());wound.putString("face",face.name());
            wound.putDouble("u",.65);wound.putDouble("v",.65);wound.putString("kinds","cut");wound.putString("severity","moderate");wounds.add(wound);
        }
        root.put("corpse_wounds",wounds);return root;
    }
}
