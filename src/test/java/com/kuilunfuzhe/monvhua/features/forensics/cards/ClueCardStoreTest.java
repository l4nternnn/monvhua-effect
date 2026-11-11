package com.kuilunfuzhe.monvhua.features.forensics.cards;

import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ClueCardStoreTest {
    private final UUID a=UUID.randomUUID(),b=UUID.randomUUID(),outsider=UUID.randomUUID();
    private ClueCardStore store(){var s=new ClueCardStore();s.start("trial_1",Map.of(a,"甲",b,"乙"));return s;}
    private static ClueCardStore.Fields fields(String text){return new ClueCardStore.Fields("标题","约 12:00","走廊",text,"");}
    private String create(ClueCardStore s,UUID who){var snapshot=new NbtCompound();snapshot.putIntArray("pixels",new int[]{17,22});var audit=new NbtCompound();audit.putString("hidden_actor","killer");return s.create(who,fields("原始说法"),snapshot,audit);}
    @Test void privateCardsAndAuditNeverLeakThroughSummaries(){var s=store();String id=create(s,a);
        assertFalse(s.readable(b,id));assertTrue(s.summaries(b).isEmpty());assertTrue(s.summaries(outsider).isEmpty());
        assertThrows(IllegalArgumentException.class,()->s.snapshot(b,id));assertThrows(IllegalArgumentException.class,()->s.edit(b,id,0,fields("窜改")));
        assertEquals("原始说法",s.summaries(a).getFirst().fields().event());
    }
    @Test void copyRetainsOriginalAndPublicVersionCannotBeRewritten(){var s=store();String original=create(s,a),copy=s.duplicate(a,original);
        s.edit(a,copy,0,fields("另一个说法"));assertEquals("原始说法",s.summaries(a).stream().filter(c->c.id().equals(original)).findFirst().orElseThrow().fields().event());
        s.publish(a,copy);assertTrue(s.readable(b,copy));assertFalse(s.readable(b,original));
        assertThrows(IllegalArgumentException.class,()->s.edit(a,copy,1,fields("事后替换")));
        var pixels=s.snapshot(a,copy);pixels.putIntArray("pixels",new int[]{999});assertArrayEquals(new int[]{17,22},s.snapshot(b,copy).getIntArray("pixels").orElseThrow());
        assertTrue(s.collect(b,copy));assertFalse(s.collect(b,copy));
    }
    @Test void comparisonValidatesBothCardsBeforePublishingAnything(){var s=store();String mine=create(s,a),theirs=create(s,b);
        assertThrows(IllegalArgumentException.class,()->s.compare(a,mine,theirs));assertFalse(s.summaries(a).getFirst().locked());
        String other=s.duplicate(a,mine);s.compare(a,mine,other);assertArrayEquals(new String[]{mine,other},s.latest(b));
        assertTrue(s.summaries(b).stream().filter(c->!c.owned()).allMatch(ClueCardStore.Summary::locked));
    }
    @Test void serializationRestoresLocksCopiesAudienceAndLatestComparison(){var s=store();String first=create(s,a),second=s.duplicate(a,first);s.compare(a,first,second);s.collect(b,first);s.setInBag(a,first,false);
        var encoded=ClueCardStore.CODEC.encodeStart(NbtOps.INSTANCE,s).getOrThrow();var restored=ClueCardStore.CODEC.parse(NbtOps.INSTANCE,encoded).getOrThrow();
        assertArrayEquals(s.latest(b),restored.latest(b));assertEquals(s.summaries(a),restored.summaries(a));assertFalse(restored.inBag(a,first));
        assertThrows(IllegalArgumentException.class,()->restored.edit(a,first,0,fields("重启绕过")));
        assertFalse(restored.collect(b,first));assertThrows(IllegalArgumentException.class,()->restored.start("trial_1",Map.of(a,"甲")));
    }
    @Test void stageAndRevisionAreAuthoritativeAndNewSessionDoesNotExposeOldCards(){var s=store();String id=create(s,a);s.edit(a,id,0,fields("更新"));
        assertThrows(IllegalArgumentException.class,()->s.edit(a,id,0,fields("旧窗口")));s.closeRecording();
        assertThrows(IllegalArgumentException.class,()->create(s,a));assertThrows(IllegalArgumentException.class,()->s.duplicate(a,id));s.edit(a,id,1,fields("已有草稿仍可编辑"));
        s.start("trial_2",Map.of(b,"乙"));assertFalse(s.readable(a,id));assertTrue(s.summaries(b).isEmpty());
    }
    @Test void targetedShowcaseUnlocksOnlyRecipientAndDoesNotExposePrivateSourceOrCopies(){var s=store();String id=create(s,a);s.lock(a,id);s.rememberShare("share1",b,id);
        assertFalse(s.readable(b,id));assertEquals("",s.openShare(outsider,"share1"));assertEquals(id,s.openShare(b,"share1"));assertTrue(s.readable(b,id));assertTrue(s.latest(b)[0].isEmpty());
        assertThrows(IllegalArgumentException.class,()->s.edit(a,id,0,fields("锁后修改")));
        String copy=s.duplicate(a,id);assertFalse(s.readable(b,copy));s.publish(b,id);assertTrue(s.summaries(a).stream().filter(c->c.id().equals(id)).findFirst().orElseThrow().locked());
    }
    @Test void importingKnownWoundIsIdempotentAndPublishingOneChildDoesNotExposeSiblings(){var s=store();var snapshot=new NbtCompound();
        String child=s.importWound(a,"corpse/wound1","corpse","遗体甲",fields("伤口一"),snapshot);
        assertEquals(child,s.importWound(a,"corpse/wound1","corpse","遗体甲",fields("重复导入"),snapshot));
        String sibling=s.importWound(a,"corpse/wound2","corpse","遗体甲",fields("伤口二"),snapshot);
        s.publish(a,child);assertEquals(1,s.summaries(b).size());assertEquals("遗体甲",s.summaries(b).getFirst().groupLabel());assertFalse(s.readable(b,sibling));
        assertEquals("伤口一",s.summaries(b).getFirst().fields().event());
        String copy=s.duplicate(a,child);assertNotEquals(child,copy);assertFalse(s.readable(b,copy));
    }
    @Test void fieldsCannotInjectControlCodesAndCapacityIsEnforced(){var s=store();var sanitized=new ClueCardStore.Fields("A\u0000B§c","x".repeat(60),null,"event","");assertEquals("ABc",sanitized.title());assertEquals(32,sanitized.time().length());assertEquals("",sanitized.place());
        for(int i=0;i<64;i++)create(s,a);assertThrows(IllegalArgumentException.class,()->create(s,a));
    }
}
