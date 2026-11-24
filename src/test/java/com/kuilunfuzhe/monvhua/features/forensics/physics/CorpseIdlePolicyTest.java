package com.kuilunfuzhe.monvhua.features.forensics.physics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CorpseIdlePolicyTest {
    private static CorpsePhysicsBridge.ResidentState state(String phase,boolean paused) {
        return new CorpsePhysicsBridge.ResidentState(phase,100,2,1,paused,null,"");
    }
    @Test void idleBodiesStopButManipulationAndPendingTerrainCanAdvance() {
        for(var phase:new String[]{"SLEEPING","SUPPORTED","PINNED"}) {
            assertTrue(CorpseIdlePolicy.resting(state(phase,false)));
            assertFalse(CorpseIdlePolicy.advance(state(phase,false),false,false));
            assertTrue(CorpseIdlePolicy.advance(state(phase,false),true,false));
            assertTrue(CorpseIdlePolicy.advance(state(phase,false),false,true));
            assertFalse(CorpseIdlePolicy.advance(state(phase,true),true,true));
        }
    }
    @Test void activeBodiesContinueAndClosedFailedOrPausedBodiesNeverAdvance() {
        for(var phase:new String[]{"STARTING","ACTIVE"}) {
            assertFalse(CorpseIdlePolicy.resting(state(phase,false)));
            assertTrue(CorpseIdlePolicy.advance(state(phase,false),false,false));
            assertFalse(CorpseIdlePolicy.advance(state(phase,true),true,true));
        }
        assertFalse(CorpseIdlePolicy.advance(state("FAILED",false),true,true));
        assertFalse(CorpseIdlePolicy.advance(state("CLOSED",false),true,true));
        assertFalse(CorpseIdlePolicy.advance(null,false,false));
    }
}
