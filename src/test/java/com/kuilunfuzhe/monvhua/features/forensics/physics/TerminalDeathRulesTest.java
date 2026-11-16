package com.kuilunfuzhe.monvhua.features.forensics.physics;

import com.kuilunfuzhe.monvhua.features.forensics.ForensicStore;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TerminalDeathRulesTest {
    @Test void deathDisconnectAndSpectatorShareTheSameLifeAndCannotSpawnTwice(){
        for(String first:new String[]{"death","disconnect","spectator"}){
            var store=new ForensicStore();var player=UUID.randomUUID();var life=store.life(player);
            DownedEvidence.begin(life,10);
            assertTrue(TerminalDeathRules.shouldFinalize(life,!first.equals("death"),!first.equals("death"),false));
            life.putString("terminal_transition",first);
            String corpse=store.freeze(player,"victim","minecraft:overworld",20,0,64,0,"player_attack");
            // freeze already owns the life before the spawn_started guard is written.
            assertFalse(TerminalDeathRules.shouldFinalize(life,false,true,false));
            life.putBoolean("spawn_started",true);
            for(String later:new String[]{"death","disconnect","spectator"})
                assertFalse(TerminalDeathRules.shouldFinalize(life,!later.equals("death"),!later.equals("death"),false));
            assertEquals(first,store.corpse(corpse).getString("terminal_transition",""));
            assertEquals(10,store.corpse(corpse).getCompoundOrEmpty("kom_downed").getLong("tick",-1));
        }
    }
    @Test void healthyOrMerelyInjuredPlayersDoNotDieFromLogoutOrSpectator(){
        var life=new NbtCompound();var hits=new NbtList();var hit=new NbtCompound();
        hit.putBoolean("potentially_lethal",true);hits.add(hit);life.put("hits",hits);
        // A candidate lethal hit can be cancelled by other mods; it is not a confirmed downing.
        assertFalse(TerminalDeathRules.shouldFinalize(life,true,true,false));
        assertTrue(TerminalDeathRules.shouldFinalize(life,false,true,false));
    }
    @Test void rescueAndServerShutdownDoNotProduceExceptionalDeaths(){
        var life=new NbtCompound();DownedEvidence.begin(life,10);
        assertFalse(TerminalDeathRules.shouldFinalize(life,true,false,false),"A living downed player is still rescuable");
        assertFalse(TerminalDeathRules.shouldFinalize(life,true,true,true));
        assertFalse(TerminalDeathRules.shouldFinalize(life,false,true,true));
        // Countdown removes only the visual proxy, so context still authorizes terminal exit.
        assertTrue(TerminalDeathRules.shouldFinalize(life,true,true,false));
        DownedEvidence.rescued(life);
        assertFalse(TerminalDeathRules.shouldFinalize(life,true,true,false));
    }
}
