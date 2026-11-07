package com.kuilunfuzhe.monvhua.features.forensics;

import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EvidenceSnapshotsTest {
    @Test void publicProjectionDoesNotSendTruthInventoryOrUncollectedWounds() {
        NbtCompound source=new NbtCompound();source.putString("local_skin","steve");source.putFloat("pose_head_pitch",35);
        source.putString("killer","secret");source.putString("body_part_inv_items","secret");source.put("corpse_wounds",new NbtList());
        var visual=EvidenceSnapshots.visualOnly(source);
        assertEquals(35,visual.getFloat("pose_head_pitch",0));assertFalse(visual.contains("killer"));
        assertFalse(visual.contains("body_part_inv_items"));assertFalse(visual.contains("corpse_wounds"));
        NbtCompound hit=hit("visible");hit.putString("attacker","secret");hit.putFloat("amount",9);
        NbtCompound surface=new NbtCompound();surface.putString("surface","head");surface.putString("face","NORTH");surface.putDouble("u",.4);surface.putDouble("v",.6);surface.putString("secret","hidden");hit.put("wound_surface",surface);
        var wound=EvidenceSnapshots.publicWound(hit);
        assertEquals(.4,wound.getDouble("u",0));assertFalse(wound.contains("attacker"));assertFalse(wound.contains("amount"));assertFalse(wound.contains("secret"));
    }
    @Test void observationsPersistAndArePrivateToViewerAndCase() {
        var store=new ForensicStore();UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        NbtCompound observed=new NbtCompound();observed.putString("name","Bot A");observed.putLong("revision",10);
        store.observe(a,"case-a",observed);store.collect(a,"case-a","head",List.of(hit("one")));
        store.observe(b,"case-b",observed);store.collect(b,"case-b","head",List.of(hit("secret")));
        observed.putString("name","changed outside");
        var encoded=ForensicStore.CODEC.encodeStart(NbtOps.INSTANCE,store).getOrThrow();
        var restored=ForensicStore.CODEC.parse(NbtOps.INSTANCE,encoded).getOrThrow();
        var result=EvidenceSnapshots.summary(restored,a,"");
        assertEquals("case-a",result.getString("id",""));assertEquals(1,result.getListOrEmpty("cases").size());
        assertEquals("Bot A",result.getCompoundOrEmpty("observation").getString("name",""));
        assertEquals("one",result.getListOrEmpty("wounds").getCompound(0).orElseThrow().getString("id",""));
        assertFalse(EvidenceSnapshots.summary(restored,a,"case-b").contains("observation"));
    }
    @Test void oldCollectionsRemainAccessibleWithoutInventedAppearance() {
        var store=new ForensicStore();UUID viewer=UUID.randomUUID();
        store.collect(viewer,"legacy","head",List.of(hit("old")));
        var result=EvidenceSnapshots.summary(store,viewer,"");
        assertTrue(result.getCompoundOrEmpty("observation").getBoolean("missing",false));
        assertEquals(1,result.getListOrEmpty("wounds").size());
        assertFalse(result.getCompoundOrEmpty("observation").contains("profile"));
    }
    private static NbtCompound hit(String id){NbtCompound n=new NbtCompound();n.putString("id",id);n.putString("part","head");n.putString("kinds","cut");return n;}
}
