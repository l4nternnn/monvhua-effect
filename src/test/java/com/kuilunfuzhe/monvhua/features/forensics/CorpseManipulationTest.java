package com.kuilunfuzhe.monvhua.features.forensics;

import com.kuilunfuzhe.monvhua.features.forensics.physics.*;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CorpseManipulationTest {
    @BeforeAll static void bootstrap(){SharedConstants.createGameVersion();Bootstrap.initialize();}
    @Test void pinnedSelectionDoesNotDependOnClientsWholeBodyMode(){
        for(String part:List.of("head","left_arm","right_arm","left_leg","right_leg")){
            assertTrue(CorpseGrabController.localSelection(false,"none",part,6));
            assertTrue(CorpseGrabController.localSelection(true,"none",part,6));
            assertTrue(CorpseGrabController.localSelection(false,"none",part,2));
        }
        assertFalse(CorpseGrabController.localSelection(false,null,"left_arm",6));
        assertTrue(CorpseGrabController.localSelection(true,null,"left_arm",6));
        assertFalse(CorpseGrabController.localSelection(true,"none","torso",6));
        assertFalse(CorpseGrabController.localSelection(false,"none","left_arm",1));
        assertTrue(CorpseGrabController.localSelection(false,"right_arm","left_arm",6));
    }
    @Test void pickupOnlyAcceptsAnIndependentMatchingHeadOrLimb(){
        for(String part:List.of("head","left_arm","right_arm","left_leg","right_leg")){
            assertTrue(CorpseTransportController.permits("pickup",part,Set.of(part)));
            assertFalse(CorpseTransportController.permits("carry",part,Set.of(part)));
            assertFalse(CorpseTransportController.permits("drag",part,Set.of(part)));
            assertFalse(CorpseTransportController.permits("pickup","torso",Set.of(part)));
            assertFalse(CorpseTransportController.permits("pickup",part,Set.of("torso",part)));
        }
        assertFalse(CorpseTransportController.permits("pickup","torso",Set.of("torso")));
        assertFalse(CorpseTransportController.permits("pickup","unknown",Set.of("unknown")));
        assertFalse(CorpseTransportController.permits("invalid","left_arm",Set.of("left_arm")));
        assertTrue(CorpseTransportController.permits("carry","torso",Set.of("torso","head")));
        assertTrue(CorpseTransportController.permits("carry","torso",Set.of("torso")));
        assertFalse(CorpseTransportController.permits("drag","torso",Set.of("torso")));
        assertTrue(CorpseTransportController.permits("drag","left_arm",Set.of("torso","left_arm")));
        assertFalse(CorpseTransportController.permits("pickup","head",Set.copyOf(Forensics.PARTS)));
    }
}
