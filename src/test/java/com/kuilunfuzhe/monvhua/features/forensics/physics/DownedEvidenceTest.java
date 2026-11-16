package com.kuilunfuzhe.monvhua.features.forensics.physics;

import com.kuilunfuzhe.monvhua.features.forensics.ForensicStore;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DownedEvidenceTest {
    private static NbtCompound hit(long tick,boolean lethal){
        var hit=new NbtCompound();hit.putString("id","original-attack");hit.putLong("tick",tick);
        hit.putBoolean("potentially_lethal",lethal);hit.putString("attacker","real-attacker");
        hit.putString("weapon","minecraft:iron_axe");hit.putString("kinds","cut");hit.putFloat("amount",12);
        return hit;
    }
    @Test void downingDoesNotCreateCorpseAndTrueDeathRetainsOriginalAttackAndOwnTime(){
        var store=new ForensicStore();var player=UUID.randomUUID();store.append(player,hit(10,true));
        var life=store.life(player);var original=life.getListOrEmpty("hits").copy();
        DownedEvidence.begin(life,10);
        assertFalse(life.contains("corpse"));assertFalse(life.getBoolean("spawn_started",false));
        assertEquals(original,life.getListOrEmpty("hits"));
        String corpse=store.freeze(player,"victim","minecraft:overworld",300,1,64,2,"generic_kill");
        var record=store.corpse(corpse);
        assertEquals(300,record.getLong("death_tick",-1));
        assertEquals(10,record.getCompoundOrEmpty("kom_downed").getLong("tick",-1));
        assertEquals("real-attacker",record.getCompoundOrEmpty("kom_downed").getCompoundOrEmpty("hit").getString("attacker",""));
        assertEquals(original,record.getListOrEmpty("hits"));
        assertEquals(corpse,store.freeze(player,"victim","minecraft:overworld",301,1,64,2,"generic_kill"));
    }
    @Test void rescueKeepsWoundsButClearsSyntheticKillExceptionAndNextDowningGetsNewIdentity(){
        var life=new NbtCompound();var hits=new NbtList();hits.add(hit(10,true));life.put("hits",hits);
        DownedEvidence.begin(life,10);var old=life.getCompoundOrEmpty("kom_downed").copy();
        assertTrue(DownedEvidence.synthetic(life,"minecraft:generic_kill"));
        for(String type:new String[]{"minecraft:player_attack","minecraft:arrow","minecraft:fall","minecraft:generic"})
            assertFalse(DownedEvidence.synthetic(life,type));
        DownedEvidence.rescued(life);assertEquals(hits,life.getListOrEmpty("hits"));
        assertFalse(DownedEvidence.synthetic(life,"minecraft:generic_kill"));
        DownedEvidence.begin(life,20);var next=life.getCompoundOrEmpty("kom_downed");
        assertNotEquals(old.getString("session",""),next.getString("session",""));
        assertFalse(next.contains("hit"),"Do not assign an old attack to a new downing");
        assertEquals("original-attack",old.getCompoundOrEmpty("hit").getString("id",""));
    }
    @Test void snapshotSurvivesChronologicalHitCompactionWithoutInventingLethality(){
        var life=new NbtCompound();var hits=new NbtList();hits.add(hit(10,true));life.put("hits",hits);
        DownedEvidence.begin(life,10);hits.clear();
        assertEquals("minecraft:iron_axe",life.getCompoundOrEmpty("kom_downed").getCompoundOrEmpty("hit").getString("weapon",""));
        hits.add(hit(20,false));DownedEvidence.begin(life,20);
        assertFalse(life.getCompoundOrEmpty("kom_downed").contains("hit"));
    }
}
