package com.kuilunfuzhe.monvhua.features.forensics.physics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CorpsePhysicsNetworkTest {
    @Test void grabMarkersPreserveWorldCoordinatesAndRejectMissingOrInvalidPoints(){
        var actual=new net.minecraft.util.math.Vec3d(-72.125,68.75,-99.5);
        var target=new net.minecraft.util.math.Vec3d(-71.25,69.5,-98.25);
        var original=new CorpseGrabController.Status(java.util.UUID.randomUUID(),"left_arm",true,false,2.75,actual,target,"");
        var encoded=CorpsePhysicsNetwork.encodeGrabStatus(original);
        assertEquals(original,CorpsePhysicsNetwork.decodeGrabStatus(encoded));
        var missing=encoded.copy();missing.remove("actual");assertNull(CorpsePhysicsNetwork.decodeGrabStatus(missing));
        encoded.getCompoundOrEmpty("target").putDouble("x",Double.NaN);
        assertNull(CorpsePhysicsNetwork.decodeGrabStatus(encoded));
        assertNull(CorpsePhysicsNetwork.decodeGrabStatus(new net.minecraft.nbt.NbtCompound()));
    }
    @Test void rejectsNonfiniteAndExcessiveNetworkForces(){
        assertTrue(CorpsePhysicsNetwork.validInput(8,0,2,0));
        assertTrue(CorpsePhysicsNetwork.validInput(-8,1,1,1));
        assertFalse(CorpsePhysicsNetwork.validInput(8.1,0,0,0));
        assertFalse(CorpsePhysicsNetwork.validInput(0,2,1,0));
        for(double bad:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.MAX_VALUE}){
            assertFalse(CorpsePhysicsNetwork.validInput(bad,0,0,0));
            assertFalse(CorpsePhysicsNetwork.validInput(0,bad,0,0));
            assertFalse(CorpsePhysicsNetwork.validInput(0,0,bad,0));
            assertFalse(CorpsePhysicsNetwork.validInput(0,0,0,bad));
        }
    }
}
