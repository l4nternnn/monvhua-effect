package com.kuilunfuzhe.monvhua.features.forensics;

import com.kuilunfuzhe.monvhua.features.forensics.physics.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CorpsePhysicsDiagnosticsTest {
    @Test void boundedRecentEntriesPreserveCumulativeCountsAndReset(){
        CorpsePhysicsDiagnostics.clear();
        for(int i=0;i<40;i++)CorpsePhysicsDiagnostics.rejected(CorpsePhysicsDiagnostics.Stage.CAPACITY);
        assertEquals(40,CorpsePhysicsDiagnostics.latest().sequence());
        assertNull(CorpsePhysicsDiagnostics.latest().input());
        assertTrue(CorpsePhysicsDiagnostics.report().contains("16/16"));
        assertTrue(CorpsePhysicsDiagnostics.report().contains("CAPACITY=40"));
        CorpsePhysicsDiagnostics.clear();assertNull(CorpsePhysicsDiagnostics.latest());
        assertFalse(CorpsePhysicsDiagnostics.report().contains("CAPACITY="));
    }
}
