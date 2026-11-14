package com.kuilunfuzhe.monvhua.features.physics_preview;

import com.google.gson.*;
import com.kuilunfuzhe.monvhua.renderer.body.special.*;
import com.kuilunfuzhe.monvhua.features.forensics.WoundGeometry;
import net.minecraft.util.math.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.*;

/** Invoked only by the opt-in game test, against the actual baked standard model. */
final class PhysicsPreviewChecks {
    static void check(PhysicsPoseDocument document,List<CombinedBodySpecialModelRenderer.EvidenceSurface> surfaces) {
        check(document,surfaces,false);
    }
    static void check(PhysicsPoseDocument document,List<CombinedBodySpecialModelRenderer.EvidenceSurface> surfaces,boolean slim) {
        if(surfaces.size()!=11)throw new AssertionError("Missing split model surfaces");
        for(var surface:surfaces) {
            var m=surface.transform();var inverse=new Matrix4f(m).invert();
            var v=new Vector3f(.123f,.234f,.345f);
            require(inverse.transformPosition(m.transformPosition(new Vector3f(v))).distance(v)<1e-4,"Matrix inverse mismatch");
            Box b=surface.box();
            for(Direction face:Direction.values()) {
                float u=.3f,w=.6f;
                Vector3f target=new Vector3f((float)(b.minX+u*b.getLengthX()),(float)(b.minY+w*b.getLengthY()),(float)(b.minZ+u*b.getLengthZ()));
                Vector3f normal=new Vector3f(face.getOffsetX(),face.getOffsetY(),face.getOffsetZ());
                switch(face) {
                    case UP,DOWN -> {target.y=(float)(face==Direction.UP?b.minY:b.maxY);target.z=(float)(b.minZ+w*b.getLengthZ());normal.y=-normal.y;}
                    case NORTH,SOUTH -> target.z=(float)(face==Direction.NORTH?b.minZ:b.maxZ);
                    case WEST,EAST -> target.x=(float)(face==Direction.WEST?b.minX:b.maxX);
                }
                var a=m.transformPosition(new Vector3f(target).fma(.1f,normal));
                var z=m.transformPosition(new Vector3f(target).fma(-.01f,normal));
                var hit=PhysicsPreviewPicker.pick(List.of(surface),new Vec3d(a.x,a.y,a.z),new Vec3d(z.x,z.y,z.z));
                require(hit!=null&&hit.face()==face&&hit.surface().equals(surface.name()),"Wrong face: "+surface.name()+" "+face);
                require(Math.abs(hit.u()-u)<1f/256&&Math.abs(hit.v()-w)<1f/256,"UV mismatch: "+surface.name());
            }
        }
        for(String id:document.poses().keySet()) {
            var pose=document.poses().get(id);
            var inverseRigid=new Matrix4f().translate(pose.x(),pose.y(),pose.z()).rotate(pose.rotation()).invert();
            Vector3f min=new Vector3f(Float.POSITIVE_INFINITY),max=new Vector3f(Float.NEGATIVE_INFINITY);
            for(var surface:surfaces) {
                var owner=WoundGeometry.SURFACES.stream().filter(s->s.name().equals(surface.name())).findFirst().orElseThrow().part();
                if(!owner.equals(id))continue;
                var matrix=new Matrix4f(inverseRigid).mul(surface.transform());var b=surface.box();
                for(int corner=0;corner<8;corner++) {
                    var v=matrix.transformPosition(new Vector3f((float)((corner&1)==0?b.minX:b.maxX),(float)((corner&2)==0?b.minY:b.maxY),(float)((corner&4)==0?b.minZ:b.maxZ)));
                    min.min(v);max.max(v);
                }
            }
            var half=StandardBodyPhysicsAdapter.size(id,slim).mul(.5f*document.scale());
            require(max.distance(half)<1e-4&&min.distance(new Vector3f(half).negate())<1e-4,"Skin/rigid bounds differ: "+id+" min="+min+" max="+max);
        }
    }
    static void invalidInputs(String valid) {
        var root=JsonParser.parseString(valid).getAsJsonObject();
        var bad=root.deepCopy();bad.addProperty("schema_version",2);reject(bad);
        bad=root.deepCopy();bad.getAsJsonObject("finalPose").getAsJsonArray("parts").remove(0);reject(bad);
        bad=root.deepCopy();var parts=bad.getAsJsonObject("finalPose").getAsJsonArray("parts");parts.set(1,parts.get(0).deepCopy());reject(bad);
        bad=root.deepCopy();var t=bad.getAsJsonObject("finalPose").getAsJsonArray("parts").get(0).getAsJsonObject().getAsJsonObject("transform");
        for(String name:List.of("qx","qy","qz","qw"))t.addProperty(name,0);reject(bad);
        bad=root.deepCopy();bad.getAsJsonObject("definition").getAsJsonArray("parts").get(0).getAsJsonObject().addProperty("mass",999);reject(bad);
        bad=root.deepCopy();bad.getAsJsonObject("finalPose").getAsJsonArray("parts").get(0).getAsJsonObject().getAsJsonObject("transform").addProperty("x","NaN");reject(bad);
        bad=root.deepCopy();parts=bad.getAsJsonObject("finalPose").getAsJsonArray("parts");var reversed=new JsonArray();for(int i=parts.size()-1;i>=0;i--)reversed.add(parts.get(i));bad.getAsJsonObject("finalPose").add("parts",reversed);
        require(PhysicsPoseDocument.parse(valid).poses().equals(PhysicsPoseDocument.parse(bad.toString()).poses()),"Array order changed mapping");
    }
    private static void reject(JsonObject bad){try{PhysicsPoseDocument.parse(bad.toString());throw new AssertionError("Malformed input accepted");}catch(IllegalArgumentException expected){}}
    static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
