package com.kuilunfuzhe.monvhua.features.forensics.physics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CorpsePhysicsNetworkTest {
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
