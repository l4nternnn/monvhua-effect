package com.kuilunfuzhe.monvhua.features.forensics;

import com.kuilunfuzhe.monvhua.features.forensics.blood.*;
import com.kuilunfuzhe.monvhua.network.forensics.BloodTracePackets;
import io.netty.buffer.Unpooled;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.math.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

public class BloodTransferRegressionTest {
    @Test public void competingRecipientsCannotDuplicateBlood() {
        var store = new BloodTransferStore();
        assertTrue(store.add("pool", "victim", 4, 200, 0));
        assertEquals(3, store.transfer("pool", "shoe-a", 3, 4, 10));
        assertEquals(1, store.transfer("pool", "shoe-b", 3, 4, 10));
        assertEquals(0, store.available("pool", 10));
        assertEquals(4, store.available("shoe-a", 10) + store.available("shoe-b", 10));
        assertEquals(0, store.transfer("pool", "shoe-b", 3, 4, 10));
    }
    @Test public void transferDoesNotRefreshDryingAndDryEvidenceRemains() {
        var store = new BloodTransferStore(); store.add("source", "victim", 3, 100, 0);
        assertEquals(2, store.transfer("source", "hands", 2, 4, 99));
        assertEquals(0, store.available("hands", 100)); assertEquals(2, store.visible("hands"));
        assertEquals(0, store.transfer("hands", "third", 2, 4, 100));
    }
    @Test public void newWetBatchDoesNotReviveOldBlood() {
        var store = new BloodTransferStore(); store.add("pool", "a", 3, 100, 0); store.add("pool", "a", 2, 150, 50);
        assertEquals(2, store.available("pool", 110)); assertEquals(5, store.visible("pool"));
        assertEquals(2, store.transfer("pool", "shoes", 9, 4, 110)); assertEquals(0, store.available("shoes", 150));
    }
    @Test public void capacityAndSelfTransfersNeverConsumeSource() {
        var store = new BloodTransferStore(); store.add("a", "x", 5, 100, 0); store.add("b", "y", 4, 100, 0);
        assertEquals(0, store.transfer("a", "b", 2, 4, 10));
        assertEquals(0, store.transfer("a", "a", 2, 10, 10)); assertEquals(5, store.available("a", 10));
    }
    @Test public void finiteFootprintConsumptionAndMixturesSurviveReload() {
        var store = new BloodTransferStore(); store.add("shoe", "a", 2, 100, 0); store.add("shoe", "b", 2, 100, 0);
        assertEquals("mixed", store.sourceSummary("shoe", 1)); assertEquals(1, store.consume("shoe", 1, 1));
        var restored = BloodTransferStore.CODEC.parse(NbtOps.INSTANCE, BloodTransferStore.CODEC.encodeStart(NbtOps.INSTANCE, store).getOrThrow()).getOrThrow();
        assertEquals(3, restored.available("shoe", 2)); assertEquals(3, restored.consume("shoe", 100, 2));
        assertEquals(0, restored.consume("shoe", 1, 2)); assertTrue(restored.visible("shoe") > 0);
    }
    @Test public void boundedBatchesCannotExtendExpiry() {
        var store = new BloodTransferStore();
        for (int i = 0; i < 20; i++) store.add("pool", "same", 1, 100 + i, 0);
        assertTrue(store.record("pool").getListOrEmpty("batches").size() <= 8);
        assertEquals(20, store.available("pool", 0)); assertEquals(0, store.available("pool", 120));
    }
    @Test public void footprintGeometryHasToeHeelAndRotation() {
        var p = new BloodPatch("foot", BlockPos.ORIGIN, Direction.UP, .5f, .5f, .2f, 3, 32768L, 1);
        assertFalse(p.covers(.5, .5)); assertTrue(p.covers(.6, .5)); assertTrue(p.covers(.4, .5));
        assertFalse(p.covers(.5, .65)); assertEquals(p, BloodPatch.read(p.write()));
    }
    @Test public void newGeometryRoundTripsWithoutPrivateSource() {
        SharedConstants.createGameVersion(); Bootstrap.initialize();
        var p = new BloodPatch(UUID.randomUUID().toString(), BlockPos.ORIGIN, Direction.UP, .5f, .5f, .16f, 3, 12345, 1);
        var buffer = new RegistryByteBuf(Unpooled.buffer(), DynamicRegistryManager.EMPTY);
        try {
            var packet = new BloodTracePackets.Sync("minecraft:overworld", 1, true, List.of(p), List.of());
            BloodTracePackets.Sync.CODEC.encode(buffer, packet); assertEquals(packet, BloodTracePackets.Sync.CODEC.decode(buffer));
        } finally { buffer.release(); }
    }
    @Test public void legacyTracesRemainReadableWithoutInventingWetRecords() {
        var root = new NbtCompound(); root.putInt("version", 1); root.put("patches", new NbtList());
        assertEquals(0, BloodTraceStore.CODEC.parse(NbtOps.INSTANCE, root).getOrThrow().size());
        assertEquals(0, new BloodTransferStore().available("legacy-event", 0));
    }
    @Test public void configRejectsUnsafeTimingAndProcessingMixinApplies() {
        var c = new ForensicConfig.Transfer(); assertTrue(c.valid()); c.sampleTicks = 0; assertFalse(c.valid());
        c = new ForensicConfig.Transfer(); c.stepDistance = Double.NaN; assertFalse(c.valid());
        SharedConstants.createGameVersion(); Bootstrap.initialize();
        assertTrue(Arrays.stream(net.minecraft.screen.ScreenHandler.class.getDeclaredMethods()).anyMatch(m -> m.getName().contains("protectEvidence")));
        assertTrue(Arrays.stream(net.minecraft.block.entity.AbstractFurnaceBlockEntity.class.getDeclaredMethods()).anyMatch(m -> m.getName().contains("preserveResidue")));
        assertTrue(Arrays.stream(net.minecraft.block.CrafterBlock.class.getDeclaredMethods()).anyMatch(m -> m.getName().contains("preserveResidue")));
    }
}
