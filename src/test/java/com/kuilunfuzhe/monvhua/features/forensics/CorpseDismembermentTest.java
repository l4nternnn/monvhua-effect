package com.kuilunfuzhe.monvhua.features.forensics;

import com.kuilunfuzhe.monvhua.features.forensics.physics.CorpseDismemberment;
import com.kuilunfuzhe.monvhua.features.physics_preview.*;
import com.klfz.simplephysics.api.RagdollDefinition;
import net.minecraft.nbt.*;
import net.minecraft.util.math.*;
import org.joml.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CorpseDismembermentTest {
    @Test void groundedRisingRestrictedAndUnchargedAttacksDoNotCount(){
        assertTrue(CorpseDismemberment.downwardStrike(false,false,.1,.9f));
        assertFalse(CorpseDismemberment.downwardStrike(true,false,.1,1));
        assertFalse(CorpseDismemberment.downwardStrike(false,true,.1,1));
        assertFalse(CorpseDismemberment.downwardStrike(false,false,0,1));
        assertFalse(CorpseDismemberment.downwardStrike(false,false,-1,1));
        assertFalse(CorpseDismemberment.downwardStrike(false,false,.1,.89f));
        assertFalse(CorpseDismemberment.downwardStrike(false,false,Double.NaN,1));
        assertFalse(CorpseDismemberment.downwardStrike(false,false,.1,Float.NaN));
    }
    private List<CorpsePoseGeometry.Surface> surfaces(boolean slim,float scale,boolean rotated){
        var definition=slim?RagdollDefinition.slim(scale):RagdollDefinition.standard(scale);
        var poses=new LinkedHashMap<String,PhysicsPoseDocument.Pose>();
        var global=new Quaternionf().rotateXYZ(rotated?1.4f:0,rotated?.7f:0,rotated?-.6f:0);
        for(var p:definition.parts()){
            var c=global.transform(new Vector3f(p.center().x(),p.center().y(),p.center().z()));
            // Extra arm rotation represents a posed, bent shoulder rather than merely root rotation.
            var q=new Quaternionf(global);if(rotated&&p.id().equals("left_arm"))q.rotateX(.08f);
            poses.put(p.id(),new PhysicsPoseDocument.Pose(c.x,c.y,c.z,q.x,q.y,q.z,q.w));
        }
        var record=new CorpsePoseRecord(1,"generated",slim?"slim":"default",poses,definition.id()+"_v1",scale);
        var data=new NbtCompound();data.put(CorpsePoseRecord.KEY,record.encode());
        var matrices=record.modelMatrices();
        return WoundGeometry.surfaces(data).stream().map(s->new CorpsePoseGeometry.Surface(s.name(),s.part(),s.box(),matrices.get(s.part()))).toList();
    }
    private CorpsePoseGeometry.WoundHit hit(List<CorpsePoseGeometry.Surface> surfaces,String part,float x,float y,float z){
        var s=surfaces.stream().filter(v->v.part().equals(part)).findFirst().orElseThrow();
        var world=s.matrix().transformPosition(new Vector3f(x/16,y/16,z/16));
        var wound=new NbtCompound();wound.putString("part",part);
        return new CorpsePoseGeometry.WoundHit(new Vec3d(world.x,world.y,world.z),wound);
    }
    @Test void jointSelectionUsesPosedGeometryAndUniformScaleForBothArmModels(){
        for(boolean slim:new boolean[]{false,true})for(boolean rotated:new boolean[]{false,true})for(float scale:new float[]{.5f,1,1.5f,2}){
            var s=surfaces(slim,scale,rotated);
            for(String part:List.of("head","left_arm","right_arm","left_leg","right_leg")){
                boolean left=part.startsWith("left"),arm=part.endsWith("arm");
                float x=part.equals("head")?0:arm?(left?4:-4):(left?1.9f:-1.9f);
                float y=part.endsWith("leg")?12:arm&&slim?.5f:0;
                assertEquals(part,CorpseDismemberment.connection(s,Vec3d.ZERO,hit(s,part,x,y,arm||part.endsWith("leg")?2:4),scale,slim));
            }
            assertEquals("left_arm",CorpseDismemberment.connection(s,Vec3d.ZERO,hit(s,"torso",4,0,2),scale,slim));
            assertEquals("right_leg",CorpseDismemberment.connection(s,Vec3d.ZERO,hit(s,"torso",-2,12,2),scale,slim));
            assertNull(CorpseDismemberment.connection(s,Vec3d.ZERO,hit(s,"left_arm",slim?5.5f:6,11,2),scale,slim));
            assertNull(CorpseDismemberment.connection(s,Vec3d.ZERO,hit(s,"torso",0,6,2),scale,slim));
            var missing=s.stream().filter(v->!v.part().equals("left_arm")).toList();
            assertNotEquals("left_arm",CorpseDismemberment.connection(missing,Vec3d.ZERO,hit(s,"torso",4,0,2),scale,slim));
            var singleton=s.stream().filter(v->v.part().equals("left_arm")).toList();
            assertNull(CorpseDismemberment.connection(singleton,Vec3d.ZERO,hit(s,"left_arm",4,0,2),scale,slim));
        }
    }
    @Test void independentProgressSurvivesSerializationAndFailureWithoutDuplicateCuts(){
        var corpse=new NbtCompound();var original=new NbtList();corpse.put("hits",original);var blood=new NbtCompound();blood.putInt("balance",5);corpse.put("blood",blood.copy());
        assertEquals(1,CorpseDismemberment.registerCut(corpse,"left_arm","a"));
        assertEquals(0,CorpseDismemberment.registerCut(corpse,"left_arm","a"));
        assertEquals(1,CorpseDismemberment.registerCut(corpse,"right_arm","b"));
        corpse=corpse.copy();
        assertEquals(2,CorpseDismemberment.registerCut(corpse,"left_arm","c"));
        // A failed transaction can retry with a distinct strike, but cannot replay its last strike.
        assertEquals(0,CorpseDismemberment.registerCut(corpse,"left_arm","c"));
        assertEquals(2,CorpseDismemberment.registerCut(corpse,"left_arm","d"));
        CorpseDismemberment.committed(corpse,"left_arm");
        assertEquals(0,CorpseDismemberment.registerCut(corpse,"left_arm","e"));
        assertEquals(0,CorpseDismemberment.registerCut(corpse,"torso","f"));
        assertEquals(0,CorpseDismemberment.registerCut(corpse,"head",""));
        assertEquals(2,CorpseDismemberment.registerCut(corpse,"right_arm","g"));
        assertEquals(original,corpse.getListOrEmpty("hits"));assertEquals(blood,corpse.getCompoundOrEmpty("blood"));
    }
}
