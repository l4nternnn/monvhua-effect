package com.kuilunfuzhe.monvhua.features.forensics;

import com.kuilunfuzhe.monvhua.features.physics_preview.*;
import com.kuilunfuzhe.monvhua.features.forensics.physics.*;
import com.klfz.simplephysics.api.RagdollDefinition;
import net.minecraft.nbt.*;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CorpseSeparationTest {
    private CorpsePhysicsBridge.Transfer state(boolean slim,float scale){
        var d=slim?RagdollDefinition.slim(scale):RagdollDefinition.standard(scale);
        var poses=new LinkedHashMap<String,PhysicsPoseDocument.Pose>();var velocities=new LinkedHashMap<String,CorpsePhysicsBridge.Velocity>();
        for(var p:d.parts()) {var v=p.center();poses.put(p.id(),new PhysicsPoseDocument.Pose(2+v.x(),1+v.y(),-2+v.z(),0,0,0,1));velocities.put(p.id(),new CorpsePhysicsBridge.Velocity(.4f,.2f,-.1f,0,.6f,0));}
        return new CorpsePhysicsBridge.Transfer(new PhysicsPoseDocument("SIMULATING",0,poses,0,"test",d.id()+"_v1",scale),velocities);
    }
    @Test void sequentialPartitionsKeepWorldTransformsVelocityScaleAndUniqueOwnership(){
        for(boolean slim:new boolean[]{false,true})for(float scale:new float[]{.5f,1,1.5f,2}) {
            var original=state(slim,scale);var remaining=new HashSet<>(original.pose().poses().keySet());var ownership=new HashSet<String>();
            for(String part:List.of("head","left_arm","right_arm","left_leg","right_leg")){
                remaining.remove(part);
                for(var ids:List.of(Set.of(part),Set.copyOf(remaining))){
                    var offset=new Vec3d(2,1,-2);var transfer=CorpseSeparationService.partition(original,ids,offset);
                    var record=new CorpsePoseRecord(7,"separated",slim?"slim":"default",transfer.pose().poses(),original.pose().definition(),scale);
                    assertEquals(5,record.encode().getInt("version",0));assertEquals(record,CorpsePoseRecord.decode(record.encode()));
                    var data=new NbtCompound();data.put(CorpsePoseRecord.KEY,record.encode());
                    assertEquals(ids,WoundGeometry.surfaces(data).stream().map(WoundGeometry.Surface::part).collect(java.util.stream.Collectors.toSet()));
                    for(String id:ids){var old=original.pose().poses().get(id);var now=transfer.pose().poses().get(id);assertEquals(old.x(),now.x()+offset.x,1e-6);assertEquals(old.y(),now.y()+offset.y,1e-6);assertEquals(old.z(),now.z()+offset.z,1e-6);assertEquals(original.velocities().get(id),transfer.velocities().get(id));
                        var center=StandardBodyPhysicsAdapter.matrix(id,now,slim,scale).transformPosition(StandardBodyPhysicsAdapter.center(id,slim).div(16));assertTrue(center.distance(new Vector3f(now.x(),now.y(),now.z()))<1e-5);
                    }
                }
                assertTrue(ownership.add(part));assertFalse(remaining.contains(part));
            }
            assertEquals(Set.of("torso"),remaining);assertEquals(5,ownership.size());
        }
    }
    @Test void missingPartsAndOldSchemaCannotMasqueradeAsWholeBodies(){
        var s=state(false,1);var subset=CorpseSeparationService.partition(s,Set.of("left_arm"),Vec3d.ZERO);
        var record=new CorpsePoseRecord(2,"separated","default",subset.pose().poses(),s.pose().definition(),1);
        var encoded=record.encode();encoded.putInt("version",1);encoded.putString("source","generated");encoded.putString("state","SETTLED");assertThrows(IllegalArgumentException.class,()->CorpsePoseRecord.decode(encoded));
        assertThrows(IllegalArgumentException.class,()->CorpseSeparationService.partition(s,Set.of("left_arm","head"),Vec3d.ZERO));
        assertThrows(IllegalArgumentException.class,()->CorpseSeparationService.partition(s,Set.of("tail"),Vec3d.ZERO));
    }
    @Test void independentVisualsDoNotCopyInventoryOrOtherPartsWounds(){
        var s=state(false,1);var p=s.pose().poses();var arm=new CorpsePoseRecord(2,"separated","default",Map.of("left_arm",p.get("left_arm")),s.pose().definition(),1);
        var source=new NbtCompound();source.putString("local_skin","test");var inventory=new NbtList();var item=new NbtCompound();item.putString("id","valuable");inventory.add(item);source.put("body_part_inv_items",inventory);
        for(String key:List.of("corpse_wounds","model_paint_faces")){var marks=new NbtList();for(String surface:List.of("left_arm_upper","head","body_upper")){var mark=new NbtCompound();mark.putString("surface",surface);marks.add(mark);}source.put(key,marks);}
        var visual=CorpseSeparationService.visualData(source,arm,true);
        assertFalse(visual.contains("body_part_inv_items"));assertTrue(source.contains("body_part_inv_items"));assertEquals("test",visual.getString("local_skin",""));
        for(String key:List.of("corpse_wounds","model_paint_faces")){assertEquals(1,visual.getListOrEmpty(key).size());assertEquals("left_arm_upper",visual.getListOrEmpty(key).getCompound(0).orElseThrow().getString("surface",""));}
        assertTrue(CorpseSeparationService.visualData(source,arm,false).contains("body_part_inv_items"));
    }
    @Test void newLimbObservationsKeepOtherPartsHistoricalViewsWithoutRecursiveCopies(){
        var first=new NbtCompound();first.putString("name","first");first.putLong("revision",1);
        var head=EvidenceSnapshots.withPartView(new NbtCompound(),first,"head");
        var next=new NbtCompound();next.putString("name","detached-arm");next.putLong("revision",2);
        var arm=EvidenceSnapshots.withPartView(head,next,"left_arm");
        assertEquals("first",EvidenceSnapshots.viewForPart(arm,"head").getString("name",""));
        assertEquals("detached-arm",EvidenceSnapshots.viewForPart(arm,"left_arm").getString("name",""));
        for(var part:arm.getCompoundOrEmpty("part_views").getKeys())assertFalse(arm.getCompoundOrEmpty("part_views").getCompoundOrEmpty(part).contains("part_views"));
        assertTrue(arm.getCompoundOrEmpty("part_views").getKeys().size()<=6);
    }
}
