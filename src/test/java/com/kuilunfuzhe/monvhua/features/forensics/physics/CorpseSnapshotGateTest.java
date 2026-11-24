package com.kuilunfuzhe.monvhua.features.forensics.physics;

import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CorpseSnapshotGateTest {
    private static NbtCompound snapshot(String dimension,String id) {
        var n=new NbtCompound();n.putString("dimension",dimension);var views=new NbtList();
        if(!id.isEmpty()){var view=new NbtCompound();view.putString("id",id);view.putLong("tick",10);views.add(view);}
        n.put("views",views);return n;
    }
    @Test void unchangedSnapshotHasHeartbeatWellInsideClientLease() {
        var gate=new CorpseSnapshotGate();var n=snapshot("minecraft:overworld","body");
        assertTrue(gate.shouldSend(n,0));gate.sent(n,0);
        assertFalse(gate.shouldSend(n.copy(),100_000_000));
        assertFalse(gate.shouldSend(n,499_999_999));
        assertTrue(gate.shouldSend(n,500_000_000));
        gate.sent(n,500_000_000);
        assertFalse(gate.shouldSend(n,600_000_000));
    }
    @Test void removalDimensionChangesTimelineAndGrabReleaseAreNeverSuppressed() {
        var gate=new CorpseSnapshotGate();var n=snapshot("minecraft:overworld","body");gate.sent(n,0);
        assertTrue(gate.shouldSend(snapshot("minecraft:overworld",""),1));
        assertTrue(gate.shouldSend(snapshot("minecraft:the_nether","body"),1));
        n.put("grab",new NbtCompound());assertTrue(gate.shouldSend(n,1));gate.sent(n,1);
        n.remove("grab");assertTrue(gate.shouldSend(n,2));gate.sent(n,2);
        var timeline=new NbtCompound();timeline.putLong("selected",12);n.put("timeline",timeline);gate.sent(n,3);
        timeline.putLong("selected",11);assertTrue(gate.shouldSend(n,4));
        gate.sent(n,4);n.remove("timeline");assertTrue(gate.shouldSend(n,5));
    }
    @Test void capturesCopiesAndDoesNotConsumeChangesBeforeSuccessfulSend() {
        var gate=new CorpseSnapshotGate();var n=snapshot("minecraft:overworld","body");gate.sent(n,0);
        n.getListOrEmpty("views").getCompoundOrEmpty(0).putLong("tick",11);
        assertTrue(gate.shouldSend(n,1));assertTrue(gate.shouldSend(n,2));
        gate.sent(n,2);assertFalse(gate.shouldSend(n,3));
        assertTrue(new CorpseSnapshotGate().shouldSend(n,3)); // A fresh/reconnected observer gets a full snapshot.
    }
}
