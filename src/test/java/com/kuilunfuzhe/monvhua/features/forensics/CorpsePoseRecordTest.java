package com.kuilunfuzhe.monvhua.features.forensics;

import com.kuilunfuzhe.monvhua.features.physics_preview.*;
import net.minecraft.nbt.*;
import org.joml.*;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class CorpsePoseRecordTest {
    @Test void slimDefinitionUsesItsOwnShoulderAnchorsAndVersion() throws Exception {
        try(var in=getClass().getResourceAsStream("/assets/monvhua/physics_preview/slim-six-definition.json")) {
            var definition=com.google.gson.JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
            var parts=new java.util.LinkedHashMap<String,PhysicsPoseDocument.Pose>();
            for(var element:definition.getAsJsonArray("parts")) {
                var p=element.getAsJsonObject();var c=p.getAsJsonObject("center");
                parts.put(p.get("id").getAsString(),new PhysicsPoseDocument.Pose(c.get("x").getAsFloat(),c.get("y").getAsFloat(),c.get("z").getAsFloat(),0,0,0,1));
            }
            var slim=new CorpsePoseRecord(1,"generated","slim",parts,"slim_six_v1");
            assertEquals(2,slim.encode().getInt("version",0));assertEquals(slim,CorpsePoseRecord.decode(slim.encode()));
            assertThrows(IllegalArgumentException.class,()->new CorpsePoseRecord(1,"generated","slim",parts,"standard_six_v1"));
            assertThrows(IllegalArgumentException.class,()->new CorpsePoseRecord(1,"generated","default",parts,"slim_six_v1"));
        }
    }
    @Test void frozenStaticHasDistinctStateAndRoundTrips() throws Exception {
        var d=floor();var record=new CorpsePoseRecord(20,"frozen_static","slim",d.poses(),d.definition());
        var encoded=record.encode();assertEquals("STATIC",encoded.getString("state",""));assertEquals(3,encoded.getInt("version",0));
        assertEquals(record,CorpsePoseRecord.decode(encoded));
        var bad=encoded.copy();bad.putString("state","SETTLED");assertThrows(IllegalArgumentException.class,()->CorpsePoseRecord.decode(bad));
        var legacy=CorpsePoseRecord.imported(d,1,"slim").encode();legacy.putString("state","STATIC");assertThrows(IllegalArgumentException.class,()->CorpsePoseRecord.decode(legacy));
    }
    private PhysicsPoseDocument floor() throws Exception {
        try(var in=getClass().getResourceAsStream("/assets/monvhua/physics_preview/fixtures/floor.json")) {
            assertNotNull(in);return PhysicsPoseDocument.parse(new String(in.readAllBytes(),StandardCharsets.UTF_8));
        }
    }
    @Test void roundTripPreservesRevisionAndAllParts() throws Exception {
        var pose=CorpsePoseRecord.imported(floor(),17,"slim");
        assertEquals(pose,CorpsePoseRecord.decode(pose.encode()));
        assertThrows(UnsupportedOperationException.class,()->pose.parts().clear());
        var root=new NbtCompound();root.put(CorpsePoseRecord.KEY,pose.encode());
        var visual=EvidenceSnapshots.visualOnly(root);root.getCompoundOrEmpty(CorpsePoseRecord.KEY).putLong("revision",19);
        assertEquals(17,CorpsePoseRecord.read(visual).orElseThrow().revision());
    }
    @Test void unknownMalformedAndDisconnectedRecordsFallBackAsAWhole() throws Exception {
        var valid=CorpsePoseRecord.imported(floor(),1,"slim").encode();
        var root=new NbtCompound();assertTrue(CorpsePoseRecord.read(root).isEmpty());
        for(String failure:new String[]{"version","missing","quaternion","nan","disconnected","state"}) {
            var bad=valid.copy();var part=bad.getListOrEmpty("parts").getCompound(0).orElseThrow();
            switch(failure) {
                case "version"->bad.putInt("version",99);
                case "missing"->bad.getListOrEmpty("parts").remove(0);
                case "quaternion"->part.putFloat("qw",8);
                case "nan"->part.putFloat("x",Float.NaN);
                case "disconnected"->part.putFloat("x",7);
                case "state"->bad.putString("state","TIMEOUT");
            }
            root.put(CorpsePoseRecord.KEY,bad);assertTrue(CorpsePoseRecord.read(root).isEmpty(),failure);
        }
        var d=floor();assertThrows(IllegalArgumentException.class,()->CorpsePoseRecord.imported(new PhysicsPoseDocument("TIMEOUT",d.step(),d.poses(),d.tilt(),""),1,"slim"));
    }
    @Test void itemRootCompensationAndSlimSurfaceExtentsMatchPhysicsSpace() throws Exception {
        var d=floor();
        for(boolean slim:new boolean[]{false,true}) {
            var record=CorpsePoseRecord.imported(d,1,slim?"slim":"default");var n=new NbtCompound();n.put(CorpsePoseRecord.KEY,record.encode());
            // Reproduces vanilla ItemDisplay NONE and the special renderer base, independently of compensation.
            Matrix4f base=new Matrix4f().rotateY((float)java.lang.Math.PI).translate(-.5f,-.5f,-.5f)
                    .translate(.5f,0,.5f).rotateY((float)java.lang.Math.PI).scale(-1,-1,1);
            for(String id:PhysicsPoseDocument.IDS) {
                var expected=StandardBodyPhysicsAdapter.matrix(id,d.poses().get(id),slim);
                assertTrue(new Matrix4f(base).mul(record.modelMatrices().get(id)).equals(expected,1e-5f));
                var pose=d.poses().get(id);var inv=new Matrix4f().translate(pose.x(),pose.y(),pose.z()).rotate(pose.rotation()).invert();
                var min=new Vector3f(Float.POSITIVE_INFINITY);var max=new Vector3f(Float.NEGATIVE_INFINITY);
                for(var surface:WoundGeometry.surfaces(n))if(surface.part().equals(id)) {
                    var m=new Matrix4f(inv).mul(base).mul(CorpseSurfacePose.transform(surface,n));var b=surface.box();
                    for(int i=0;i<8;i++){var v=m.transformPosition(new Vector3f((float)((i&1)==0?b.minX:b.maxX)/16,(float)((i&2)==0?b.minY:b.maxY)/16,(float)((i&4)==0?b.minZ:b.maxZ)/16));min.min(v);max.max(v);}
                }
                var half=StandardBodyPhysicsAdapter.size(id,slim).mul(.5f);
                assertTrue(max.distance(half)<1e-4,id);assertTrue(min.distance(new Vector3f(half).negate())<1e-4,id);
            }
        }
    }
}
