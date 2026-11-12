package com.kuilunfuzhe.monvhua.features.forensics.cards;

import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CorpseCardCostTest {
    private final UUID a=UUID.randomUUID(),b=UUID.randomUUID();
    private final ClueCardStore.Fields fields=new ClueCardStore.Fields("伤情","","","","办理记录");
    private ClueCardStore store(){var s=new ClueCardStore();s.start("case",Map.of(a,"甲",b,"乙"));return s;}
    private ClueCardStore.WoundImport wound(ClueCardStore s,UUID who,String corpse,String hit,int blanks){return s.importWound(who,corpse+"/"+hit,corpse,"遗体",fields,new NbtCompound(),blanks);}
    private ClueCardStore restore(ClueCardStore s){return ClueCardStore.CODEC.parse(NbtOps.INSTANCE,ClueCardStore.CODEC.encodeStart(NbtOps.INSTANCE,s).getOrThrow()).getOrThrow();}
    @Test void eightBlanksBecomeSevenRegardlessOfWoundsRepeatedImportsAndCopies(){
        var s=store();int blanks=8;
        var first=wound(s,a,"corpse","hit0",blanks);blanks-=first.blankCost();assertEquals(7,blanks);assertTrue(first.created());
        for(int i=1;i<24;i++){var next=wound(s,a,"corpse","hit"+i,blanks);blanks-=next.blankCost();assertEquals(0,next.blankCost());assertTrue(next.created());}
        var repeated=wound(s,a,"corpse","hit0",0);assertFalse(repeated.created());assertEquals(first.id(),repeated.id());assertEquals(0,repeated.blankCost());
        assertEquals(0,s.copyCost(a,first.id()));String copy=s.duplicate(a,first.id());assertEquals(0,s.copyCost(a,copy));assertEquals(7,blanks);assertEquals(25,s.ownedCount(a));
    }
    @Test void lateWoundsAfterRestartNeedNoCardButAnotherCorpseOrPlayerNeedsTheirOwn(){
        var s=store();wound(s,a,"one","first",1);s=restore(s);assertEquals(0,wound(s,a,"one","late",0).blankCost());
        var restored=s;assertThrows(IllegalArgumentException.class,()->wound(restored,a,"two","first",0));assertFalse(s.corpseRecorded(a,"two"));
        assertThrows(IllegalArgumentException.class,()->wound(restored,b,"one","first",0));assertEquals(1,wound(s,b,"one","first",1).blankCost());assertEquals(1,wound(s,a,"two","first",1).blankCost());
    }
    @Test void failedRecordingDoesNotMarkCorpsePaidAndExistingGroupCanGrowAtSlotLimit(){
        var s=store();for(int i=0;i<63;i++)s.create(a,fields,new NbtCompound(),new NbtCompound());
        String first=wound(s,a,"one","first",1).id();assertEquals(0,wound(s,a,"one","late",0).blankCost());assertDoesNotThrow(()->s.duplicate(a,first));
        assertThrows(IllegalArgumentException.class,()->wound(s,a,"two","first",1));assertFalse(s.corpseRecorded(a,"two"));
        assertThrows(IllegalArgumentException.class,()->s.create(a,fields,new NbtCompound(),new NbtCompound()));
    }
    @Test void paidCorpseAndObservationMoveWithReboundIdentityButNotIntoAnotherCase(){
        var s=store();s.observeCorpse(a,"corpse");wound(s,a,"corpse","first",1);var next=UUID.randomUUID();s.rebindMember("case","甲",next,"新甲");
        assertTrue(s.canImportCorpse(next,"corpse"));assertEquals(0,wound(s,next,"corpse","late",0).blankCost());
        s.start("other",Map.of(next,"新甲"));assertFalse(s.corpseRecorded(next,"corpse"));assertFalse(s.canImportCorpse(next,"corpse"));
        s.observeCorpse(next,"corpse");assertTrue(s.canImportCorpse(next,"corpse"));assertEquals(1,wound(s,next,"corpse","first",1).blankCost());
    }
    @Test void closedCaseCannotRecordNewWoundsAndOrdinaryCopiesStillCostOne(){
        var s=store();String normal=s.create(a,fields,new NbtCompound(),new NbtCompound());assertEquals(1,s.copyCost(a,normal));wound(s,a,"corpse","first",1);s.closeRecording();
        assertThrows(IllegalArgumentException.class,()->wound(s,a,"corpse","late",0));assertEquals(2,s.ownedCount(a));
    }
    @Test void legacyImportedWoundsAreGrandfatheredWithoutRetroactiveCharge(){
        var s=store();String id=wound(s,a,"corpse","first",1).id();var n=(NbtCompound)ClueCardStore.CODEC.encodeStart(NbtOps.INSTANCE,s).getOrThrow();n.getCompoundOrEmpty("runs").getCompoundOrEmpty("case").remove("corpse_records");
        var old=ClueCardStore.CODEC.parse(NbtOps.INSTANCE,n).getOrThrow();assertTrue(old.corpseRecorded(a,"corpse"));assertEquals(0,wound(old,a,"corpse","late",0).blankCost());assertTrue(old.owns(a,id));
    }
}
