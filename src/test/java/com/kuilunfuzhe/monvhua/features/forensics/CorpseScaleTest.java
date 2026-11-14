package com.kuilunfuzhe.monvhua.features.forensics;

import com.google.gson.*;
import com.kuilunfuzhe.monvhua.features.physics_preview.*;
import com.kuilunfuzhe.monvhua.features.forensics.physics.*;
import net.minecraft.nbt.NbtCompound;
import org.joml.*;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CorpseScaleTest {
    private static JsonObject resource(String name) throws Exception {
        try(var in=CorpseScaleTest.class.getResourceAsStream("/assets/monvhua/physics_preview/"+name)) {
            assertNotNull(in);return JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    @Test void bothShapesRoundTripAtAllAcceptanceScalesAndMatchWorldGeometry() throws Exception {
        Matrix4f base=new Matrix4f().rotateY((float)java.lang.Math.PI).translate(-.5f,-.5f,-.5f)
                .translate(.5f,0,.5f).rotateY((float)java.lang.Math.PI).scale(-1,-1,1);
        for(boolean slim:new boolean[]{false,true})for(float scale:new float[]{.5f,1,1.5f,2}) {
            var definition=resource((slim?"slim":"standard")+"-six-definition.json");
            Map<String,PhysicsPoseDocument.Pose> parts=new LinkedHashMap<>();
            // Translate away from zero to detect accidentally scaling world positions a second time.
            for(var e:definition.getAsJsonArray("parts")) {
                var part=e.getAsJsonObject();var c=part.getAsJsonObject("center");
                parts.put(part.get("id").getAsString(),new PhysicsPoseDocument.Pose(2+c.get("x").getAsFloat()*scale,
                        -1+c.get("y").getAsFloat()*scale,3+c.get("z").getAsFloat()*scale,0,0,0,1));
            }
            for(String source:List.of("generated","frozen_static")) {
                var record=new CorpsePoseRecord(9,source,slim?"slim":"default",parts,slim?"slim_six_v1":"standard_six_v1",scale);
                assertEquals(record,CorpsePoseRecord.decode(record.encode()));
                var data=new NbtCompound();data.put(CorpsePoseRecord.KEY,record.encode());data.putFloat("corpse_scale",scale);
                for(String id:PhysicsPoseDocument.IDS) {
                    var pose=parts.get(id);var matrix=new Matrix4f(base).mul(record.modelMatrices().get(id));
                    var center=matrix.transformPosition(StandardBodyPhysicsAdapter.center(id,slim).div(16));
                    assertEquals(pose.x(),center.x,1e-5);assertEquals(pose.y(),center.y,1e-5);assertEquals(pose.z(),center.z,1e-5);
                    var inverse=new Matrix4f().translate(pose.x(),pose.y(),pose.z()).rotate(pose.rotation()).invert();
                    Vector3f min=new Vector3f(Float.POSITIVE_INFINITY),max=new Vector3f(Float.NEGATIVE_INFINITY);
                    for(var surface:WoundGeometry.surfaces(data))if(surface.part().equals(id)) {
                        var transform=new Matrix4f(inverse).mul(base).mul(CorpseSurfacePose.transform(surface,data));var b=surface.box();
                        for(int i=0;i<8;i++) {
                            var v=transform.transformPosition(new Vector3f((float)((i&1)==0?b.minX:b.maxX)/16,
                                    (float)((i&2)==0?b.minY:b.maxY)/16,(float)((i&4)==0?b.minZ:b.maxZ)/16));min.min(v);max.max(v);
                        }
                    }
                    var half=StandardBodyPhysicsAdapter.size(id,slim).mul(scale*.5f);
                    assertTrue(max.distance(half)<1e-4,id+" scale="+scale);assertTrue(min.distance(new Vector3f(half).negate())<1e-4,id);
                }
                if(scale!=1) {
                    var bad=record.encode();bad.remove("scale");assertThrows(IllegalArgumentException.class,()->CorpsePoseRecord.decode(bad));
                    assertThrows(IllegalArgumentException.class,()->new CorpsePoseRecord(1,source,slim?"slim":"default",parts,record.definition(),1));
                }
            }
        }
    }
    @Test void invalidScalesAreRejectedBeforePhysicsSubmission() {
        for(float scale:new float[]{Float.NaN,Float.POSITIVE_INFINITY,0,.49f,2.01f}) {
            assertFalse(CorpseScale.supported(scale));
            assertThrows(IllegalArgumentException.class,()->new CorpsePhysicsBridge.Request(true,0,0,List.of(),null,null,scale));
        }
        assertEquals(1,new CorpsePhysicsBridge.Request(true,0,0,List.of()).scale());
    }
    @Test void scaledExportsAcceptOnlyCanonicalGeometry() throws Exception {
        for(float scale:new float[]{.5f,1.5f,2}) {
            var root=resource("fixtures/floor.json");var def=root.getAsJsonObject("definition");
            for(var e:def.getAsJsonArray("parts")) {
                var p=e.getAsJsonObject();vector(p.getAsJsonObject("size"),scale);vector(p.getAsJsonObject("center"),scale);
                p.addProperty("mass",p.get("mass").getAsFloat()*scale*scale*scale);p.addProperty("inset",p.get("inset").getAsFloat()*scale);
            }
            for(var e:def.getAsJsonArray("joints")){var j=e.getAsJsonObject();vector(j.getAsJsonObject("pivotA"),scale);vector(j.getAsJsonObject("pivotB"),scale);}
            var parsed=PhysicsPoseDocument.parse(root.toString());assertEquals(scale,parsed.scale());
            def.getAsJsonArray("parts").get(0).getAsJsonObject().addProperty("mass",99);
            assertThrows(IllegalArgumentException.class,()->PhysicsPoseDocument.parse(root.toString()));
        }
    }
    private static void vector(JsonObject v,float scale){for(String key:List.of("x","y","z"))v.addProperty(key,v.get(key).getAsFloat()*scale);}
}
