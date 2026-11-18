package com.kuilunfuzhe.monvhua.features.forensics;

import com.kuilunfuzhe.monvhua.features.forensics.blood.*;
import com.kuilunfuzhe.monvhua.features.forensics.cleaning.*;
import com.kuilunfuzhe.monvhua.network.forensics.BloodTracePackets;
import net.minecraft.nbt.*;
import net.minecraft.util.math.*;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

public class CleaningRegressionTest {
    @Test void bottleProgressPersistsAndSecondWashRemovesTheRemainingBlood(){
        var store=new BloodTraceStore();var p=patch();store.submit(new BloodTraceStore.Entry(p,"source","event","stone",false));
        var first=ForensicCleaningApi.cleanPatch(p,p.center(),3,.5);
        store.submit(new BloodTraceStore.Entry(first,"source","event","stone",false));store.markBottleWashed(p.id());
        store=BloodTraceStore.CODEC.parse(NbtOps.INSTANCE,BloodTraceStore.CODEC.encodeStart(NbtOps.INSTANCE,store).getOrThrow()).getOrThrow();
        assertTrue(store.bottleWashed(p.id()));assertTrue(first.mass()>0);assertTrue(first.mass()<p.mass());
        var second=ForensicCleaningApi.cleanPatch(store.entry(p.id()).patch(),p.center(),3,1);
        assertEquals(0,second.mass());store.remove(p.id());assertFalse(store.bottleWashed(p.id()));
    }
    private BloodPatch patch(){return new BloodPatch(UUID.randomUUID().toString(),BlockPos.ORIGIN,Direction.UP,.5f,.5f,.35f,1,17,1);}
    @Test void radiusClipsCellsAndLeavesOutsideUntouched(){
        var p=patch();var origin=com.kuilunfuzhe.monvhua.features.paint.PaintSurface.point(p.pos(),p.face(),0,0,1,0);
        var center=origin.add(p.right().multiply(.3)).add(p.down().multiply(.5));
        var clean=ForensicCleaningApi.cleanPatch(p,center,.17,1);
        assertTrue(clean.mass()>0);assertTrue(clean.mass()<p.mass());
        assertEquals(0,clean.density(.3,.5));assertEquals(255,clean.density(.7,.5));
        assertEquals(p.id(),clean.id());assertEquals(p.revision(),clean.revision());
    }
    @Test void repeatedFullCleanAndZeroStrengthHaveNoFurtherEffect(){
        var p=patch();var c=ForensicCleaningApi.cleanPatch(p,p.center(),2,1);
        assertEquals(0,c.mass());assertEquals(c,ForensicCleaningApi.cleanPatch(c,c.center(),2,1));
        assertEquals(p,ForensicCleaningApi.cleanPatch(p,p.center(),2,0));
        assertThrows(IllegalArgumentException.class,()->ForensicCleaningApi.cleanPatch(p,p.center(),2,Double.NaN));
    }
    @Test void smallBodyCleansAccumulateWithoutRoundingUpAndPreviewDoesNotMutate(){
        var s=new BloodTransferStore();s.add("body","victim",4,100,0);
        var before=BloodTransferStore.CODEC.encodeStart(NbtOps.INSTANCE,s).getOrThrow();
        s.clean("body",.5,10,false);
        assertEquals(before,BloodTransferStore.CODEC.encodeStart(NbtOps.INSTANCE,s).getOrThrow());
        assertEquals(0,s.clean("body",.01,10,true).transferable());assertEquals(4,s.available("body",10));
        for(int i=0;i<24;i++)s.clean("body",.01,10,true);
        assertEquals(3,s.available("body",10));
        s.clean("body",1,10,true);assertEquals(0,s.available("body",10));assertEquals(0,s.visible("body"));
        s.add("body","new",1,200,10);assertEquals(0,s.clean("body",.01,10,true).transferable());
    }
    @Test void dryCleaningNeverCreatesWetBloodOrChangesExpiry(){
        var s=new BloodTransferStore();s.add("body","victim",4,10,0);
        assertEquals(0,s.clean("body",.5,20,true).transferable());assertEquals(2,s.visible("body"));
        assertEquals(0,s.available("body",20));
        assertEquals(10,s.record("body").getListOrEmpty("batches").getCompound(0).orElseThrow().getLong("until",0));
    }
    @Test void treatmentAndRetiredSitesSurviveSaveReload(){
        var s=new BloodTraceStore();var p=patch();s.submit(new BloodTraceStore.Entry(p,"private","event","stone",false));
        var cleaned=ForensicCleaningApi.cleanPatch(p,p.center(),2,.5);
        assertTrue(s.submit(new BloodTraceStore.Entry(cleaned,"private","event","stone",false)));
        var restored=BloodTraceStore.CODEC.parse(NbtOps.INSTANCE,BloodTraceStore.CODEC.encodeStart(NbtOps.INSTANCE,s).getOrThrow()).getOrThrow();
        assertEquals(cleaned,restored.entry(p.id()).patch());restored.remove(p.id());
        restored=BloodTraceStore.CODEC.parse(NbtOps.INSTANCE,BloodTraceStore.CODEC.encodeStart(NbtOps.INSTANCE,restored).getOrThrow()).getOrThrow();
        assertFalse(restored.submit(new BloodTraceStore.Entry(p,"private","event","stone",false)));
    }
    @Test void producerRevisionCanGrowAfterCleaningWithoutRestoringOldBlood(){
        var s=new BloodTraceStore();var p=patch();s.submit(new BloodTraceStore.Entry(p,"s","event","stone",false));
        var clean=ForensicCleaningApi.cleanPatch(p,p.center(),2,1);s.submit(new BloodTraceStore.Entry(clean,"s","event","stone",false));
        var newer=new BloodPatch(p.id(),p.pos(),p.face(),p.u(),p.v(),p.radius(),p.kind(),p.seed(),2);
        assertTrue(s.submit(new BloodTraceStore.Entry(newer,"s","event","stone",false)));
        assertEquals(0,s.entry(p.id()).patch().mass());
    }
    @Test void cleaningReceiptSurvivesReloadAndExpiresOnlyAfterRetention(){
        var s=new CleaningActionStore();s.put("action","parameters",10,2,3,4);
        var reload=CleaningActionStore.CODEC.parse(NbtOps.INSTANCE,CleaningActionStore.CODEC.encodeStart(NbtOps.INSTANCE,s).getOrThrow()).getOrThrow();
        assertEquals("parameters",reload.get("action").getString("fingerprint",""));reload.prune(72009);assertEquals(1,reload.size());reload.prune(72010);assertEquals(0,reload.size());
    }
    @Test void publicTreatmentAndDragGeometryRoundTrip(){
        SharedConstants.createGameVersion();Bootstrap.initialize();
        var p=patch();p=new BloodPatch(p.id(),p.pos(),p.face(),.5f,.5f,.3f,4,32768,2);
        assertTrue(p.covers(.7,.5));assertFalse(p.covers(.5,.7));
        p=ForensicCleaningApi.cleanPatch(p,p.center(),2,.5);
        var b=new RegistryByteBuf(Unpooled.buffer(),DynamicRegistryManager.EMPTY);
        try{var packet=new BloodTracePackets.Sync("minecraft:overworld",1,true,List.of(p),List.of());BloodTracePackets.Sync.CODEC.encode(b,packet);assertEquals(packet,BloodTracePackets.Sync.CODEC.decode(b));}finally{b.release();}
    }
    @Test void subpixelCleansAccumulateAcrossSaveReload(){
        var store=new BloodTraceStore();var p=patch();store.submit(new BloodTraceStore.Entry(p,"s","e","stone",false));
        for(int i=0;i<5;i++){
            var plan=ForensicCleaningApi.planPatch(p,p.center(),2,.001,store.cleaningRemainders(p.id()));
            assertTrue(plan.changed());store.submit(new BloodTraceStore.Entry(plan.patch(),"s","e","stone",false));store.cleaningRemainders(p.id(),plan.remainders());
            store=BloodTraceStore.CODEC.parse(NbtOps.INSTANCE,BloodTraceStore.CODEC.encodeStart(NbtOps.INSTANCE,store).getOrThrow()).getOrThrow();p=store.entry(p.id()).patch();
        }
        assertEquals(254,p.density(.5,.5));
    }
    @Test void paintFaceRevisionsInvalidateOnlyChangedFacesAndSurviveRepaint(){
        var store=new com.kuilunfuzhe.monvhua.features.paint.PaintOverlayStore();
        long empty=store.faceRevision(BlockPos.ORIGIN,Direction.UP);
        store.setPixel(BlockPos.ORIGIN,Direction.UP,1,1,0xffff0000);long painted=store.faceRevision(BlockPos.ORIGIN,Direction.UP);
        assertNotEquals(empty,painted);assertEquals(0,store.faceRevision(BlockPos.ORIGIN,Direction.NORTH));
        store.clearFace(BlockPos.ORIGIN,Direction.UP);assertEquals(empty,store.faceRevision(BlockPos.ORIGIN,Direction.UP));
        store.setPixel(BlockPos.ORIGIN,Direction.UP,1,1,0xffff0000);assertNotEquals(painted,store.faceRevision(BlockPos.ORIGIN,Direction.UP));
    }
    @Test void malformedTreatmentGridIsRejected(){
        var p=patch();assertThrows(IllegalArgumentException.class,()->p.treated("short",1));assertThrows(IllegalArgumentException.class,()->p.treated("\u0400".repeat(256),1));
    }
}
