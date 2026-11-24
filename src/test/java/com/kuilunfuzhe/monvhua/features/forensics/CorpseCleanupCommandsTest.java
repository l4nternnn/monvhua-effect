package com.kuilunfuzhe.monvhua.features.forensics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CorpseCleanupCommandsTest {
    @Test void legacyBodiesNeedNoNewTagButOrdinaryDisplaysAreExcluded(){
        assertFalse(CorpseCleanupCommands.legacyDisplay(false,false,false,false,false,false));
        for(int i=0;i<5;i++)assertTrue(CorpseCleanupCommands.legacyDisplay(false,i==0,i==1,i==2,i==3,i==4));
    }
    @Test void livingDownedProxiesAreNeverDeletedEvenIfTaggedOrLinked(){
        assertFalse(CorpseCleanupCommands.legacyDisplay(true,true,true,true,true,true));
    }
}
