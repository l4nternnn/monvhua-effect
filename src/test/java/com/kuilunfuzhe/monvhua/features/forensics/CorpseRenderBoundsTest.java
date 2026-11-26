package com.kuilunfuzhe.monvhua.features.forensics;

import com.kuilunfuzhe.monvhua.features.physics_preview.*;
import com.kuilunfuzhe.monvhua.features.forensics.physics.CorpsePhysicsBridge;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CorpseRenderBoundsTest {
    private static CorpsePhysicsBridge.TimedPose frame(int tick,float x,float scale){
        return new CorpsePhysicsBridge.TimedPose(tick,new PhysicsPoseDocument("SIMULATING",tick,
                Map.of("left_arm",new PhysicsPoseDocument.Pose(x,2,3,0,0,0,1)),0,"test","slim_six_v1",scale));
    }
    @Test void coversBothEndpointsAndEveryIntermediateRotationAtAllScales(){
        for(float scale:new float[]{.5f,1,2}){
            var box=CorpseRenderBounds.of(List.of(frame(1,-2,scale),frame(2,5,scale)),true);
            assertNotNull(box);var half=StandardBodyPhysicsAdapter.size("left_arm",true).mul(scale*.5f);
            for(int step=0;step<=20;step++)for(int corner=0;corner<8;corner++){
                var point=new org.joml.Vector3f((corner&1)==0?-half.x:half.x,(corner&2)==0?-half.y:half.y,(corner&4)==0?-half.z:half.z);
                new org.joml.Quaternionf().rotateXYZ(step*.1f,step*.2f,step*.15f).transform(point);
                point.add(-2+7*step/20f,2,3);
                assertTrue(box.contains(point.x,point.y,point.z));
            }
        }
    }
    @Test void invalidBoundsFailOpenAndWorldOffsetIsAppliedExactlyOnce(){
        assertNull(CorpseRenderBounds.of(List.of(),false));
        assertNull(CorpseRenderBounds.of(List.of(frame(1,Float.NaN,1)),false));
        var local=CorpseRenderBounds.of(List.of(frame(1,0,1)),false);
        assertNotNull(local);var world=local.offset(new net.minecraft.util.math.Vec3d(100,64,-100));
        assertEquals(local.minX+100,world.minX);assertEquals(local.minY+64,world.minY);assertEquals(local.minZ-100,world.minZ);
    }
}
