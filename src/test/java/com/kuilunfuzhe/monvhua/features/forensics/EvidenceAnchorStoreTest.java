package com.kuilunfuzhe.monvhua.features.forensics;

import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EvidenceAnchorStoreTest {
    private static EvidenceAnchorStore.Anchor anchor(UUID id,UUID owner){return new EvidenceAnchorStore.Anchor(id,owner,"minecraft:overworld",-12.5,64.25,33,90,-30,45,1.5f);}
    @Test void limitIsPerOwnerAndCannotBeBypassedByUpdatingAnotherOwnersId(){
        var store=new EvidenceAnchorStore();var owner=UUID.randomUUID();var other=UUID.randomUUID();
        for(int i=0;i<20;i++)store.put(anchor(UUID.randomUUID(),owner));
        assertThrows(IllegalArgumentException.class,()->store.put(anchor(UUID.randomUUID(),owner)));
        var id=UUID.randomUUID();store.put(anchor(id,other));
        assertEquals(20,store.owned(owner).size());assertEquals(1,store.owned(other).size());
        assertThrows(IllegalArgumentException.class,()->store.remove(owner,id));
        assertThrows(IllegalArgumentException.class,()->store.put(anchor(id,owner)));
        store.clear(owner);assertTrue(store.owned(owner).isEmpty());assertEquals(id,store.owned(other).getFirst().id());
    }
    @Test void codecRoundtripRestoresOwnerDimensionPlacementSizeAndRevision(){
        var store=new EvidenceAnchorStore();var owner=UUID.randomUUID();var a=anchor(UUID.randomUUID(),owner);store.put(a);
        var b=new EvidenceAnchorStore.Anchor(UUID.randomUUID(),owner,"minecraft:the_nether",3,70,-4,-180,0,90,2);store.put(b);
        var encoded=EvidenceAnchorStore.CODEC.encodeStart(NbtOps.INSTANCE,store).getOrThrow();
        var restored=EvidenceAnchorStore.CODEC.parse(NbtOps.INSTANCE,encoded).getOrThrow();
        assertEquals(List.of(a,b),restored.owned(owner));assertEquals(store.revision(),restored.revision());
        restored.remove(owner,a.id());assertTrue(restored.revision()>store.revision());assertEquals(List.of(b),restored.owned(owner));
    }
    @Test void invalidCoordinatesAndUnsupportedSchemaFailClosed(){
        var id=UUID.randomUUID();var owner=UUID.randomUUID();var data=anchor(id,owner).nbt();data.putDouble("x",Double.NaN);
        assertThrows(IllegalArgumentException.class,()->EvidenceAnchorStore.Anchor.read(data));
        var invalid=new NbtCompound();invalid.putInt("schema",9);
        assertThrows(IllegalStateException.class,()->EvidenceAnchorStore.CODEC.parse(NbtOps.INSTANCE,invalid).getOrThrow());
        var root=new NbtCompound();root.putInt("schema",1);var entries=new NbtList();entries.add(data);entries.add(anchor(id,owner).nbt());root.put("anchors",entries);
        var restored=EvidenceAnchorStore.CODEC.parse(NbtOps.INSTANCE,root).getOrThrow();assertEquals(1,restored.owned(owner).size());
    }
    @Test void comparisonBoardPlacementPersistsAndOldAnchorsStillOpenCorpseView(){
        var owner=UUID.randomUUID();var old=anchor(UUID.randomUUID(),owner);var legacy=old.nbt();legacy.remove("board");assertFalse(EvidenceAnchorStore.Anchor.read(legacy).board());
        var board=new EvidenceAnchorStore.Anchor(UUID.randomUUID(),owner,"minecraft:overworld",1,64,2,90,0,0,1.5f,true);
        var store=new EvidenceAnchorStore();store.put(old);store.put(board);
        var restored=EvidenceAnchorStore.CODEC.parse(NbtOps.INSTANCE,EvidenceAnchorStore.CODEC.encodeStart(NbtOps.INSTANCE,store).getOrThrow()).getOrThrow();
        assertEquals(List.of(old,board),restored.owned(owner));assertTrue(restored.require(owner,board.id()).board());
    }
}
