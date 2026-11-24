package com.kuilunfuzhe.monvhua.features.forensics.physics;

import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TerminalDeathRulesTest {
    @Test void disconnectKickAndSpectatorNeverFinalizeEvenWithStaleDowningOrZeroHealth(){
        var life=new NbtCompound();DownedEvidence.begin(life,10);
        for(boolean alive:new boolean[]{true,false}){
            assertFalse(TerminalDeathRules.shouldFinalize(life,alive,true,false));
            assertFalse(TerminalDeathRules.shouldFinalize(life,alive,true,true));
        }
    }
    @Test void onlyActualDeathCanFinalizeAndOnlyOnce(){
        var life=new NbtCompound();
        assertFalse(TerminalDeathRules.shouldFinalize(life,true,false,false));
        assertTrue(TerminalDeathRules.shouldFinalize(life,false,false,false));
        assertFalse(TerminalDeathRules.shouldFinalize(life,false,false,true));
        life.putString("corpse","already-frozen");
        assertFalse(TerminalDeathRules.shouldFinalize(life,false,false,false));
        life.remove("corpse");life.putBoolean("spawn_started",true);
        assertFalse(TerminalDeathRules.shouldFinalize(life,false,false,false));
    }
}
