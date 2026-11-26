package com.kuilunfuzhe.monvhua.features.forensics;

import com.kuilunfuzhe.monvhua.features.forensics.blood.BloodTransferStore;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BloodResidueMigrationTest {
    private static BloodTransferStore reload(BloodTransferStore store) {
        return BloodTransferStore.CODEC.parse(NbtOps.INSTANCE,
                BloodTransferStore.CODEC.encodeStart(NbtOps.INSTANCE, store).getOrThrow()).getOrThrow();
    }

    @Test void driedSourcesSurviveBatchPruningAndReload() {
        var store = new BloodTransferStore();
        store.add("weapon", "life-a", 3, 100, 0);
        assertEquals("mixed", store.sourceSummary("weapon", 100));
        assertEquals("life-a", reload(store).residueSummary("weapon"));
        store.add("weapon", "life-b", 2, 200, 100);
        assertEquals("life-b", store.sourceSummary("weapon", 100));
        assertEquals("mixed", reload(store).residueSummary("weapon"));
    }

    @Test void partialCleaningLosesCertaintyAndFullCleaningClearsSummary() {
        var store = new BloodTransferStore();
        store.add("weapon", "life-a", 4, 100, 0);
        long revision = store.revision("weapon");
        var preview = store.clean("weapon", .5, 1, false);
        assertEquals(2, preview.visible());
        assertEquals(revision, store.revision("weapon"));
        assertEquals("life-a", store.residueSummary("weapon"));
        assertEquals(preview, store.clean("weapon", .5, 1, true));
        assertEquals("unknown", reload(store).residueSummary("weapon"));
        store.add("weapon", "life-a", 1, 100, 1);
        assertEquals("unknown", store.residueSummary("weapon"));
        store.clean("weapon", 1, 1, true);
        assertEquals(0, store.visible("weapon"));
        assertEquals(0, store.available("weapon", 1));
        assertEquals("none", reload(store).residueSummary("weapon"));
        store.add("weapon", "life-b", 1, 100, 1);
        assertEquals("life-b", store.residueSummary("weapon"));
    }

    @Test void fractionalCleaningChangesRevisionWithoutPrematureSourceLoss() {
        var store = new BloodTransferStore();
        store.add("weapon", "life-a", 1, 100, 0);
        long revision = store.revision("weapon");
        assertEquals(0, store.clean("weapon", .25, 1, true).visible());
        assertTrue(store.revision("weapon") > revision);
        assertEquals("life-a", reload(store).residueSummary("weapon"));
        for (int i = 0; i < 3; i++) store.clean("weapon", .25, 1, true);
        assertEquals("none", store.residueSummary("weapon"));
    }

    @Test void wetConsumptionAndTransferKeepVisibleTraceAndAdvanceRevisions() {
        var store = new BloodTransferStore();
        store.add("a", "life-a", 4, 100, 0);
        long revision = store.revision("a");
        assertEquals(2, store.transfer("a", "b", 2, 4, 1));
        assertTrue(store.revision("a") > revision);
        assertTrue(store.revision("b") > 0);
        assertEquals("life-a", store.residueSummary("b"));
        revision = store.revision("a");
        assertEquals(2, store.consume("a", 20, 1));
        assertTrue(store.revision("a") > revision);
        assertEquals(4, store.visible("a"));
        assertEquals("life-a", reload(store).residueSummary("a"));
        revision = store.revision("a");
        assertEquals(0, store.consume("a", 1, 1));
        assertEquals(revision, store.revision("a"));
    }

    @Test void legacyVisibleHistoryRemainsUnknownButWetTransfersRemainKnown() {
        var legacy = new NbtCompound(); legacy.putInt("version", 1);
        var records = new NbtCompound(); var record = new NbtCompound();
        record.putInt("visible", 4);
        var batches = new NbtList(); var batch = new NbtCompound();
        batch.putString("source", "life-a"); batch.putInt("amount", 2); batch.putLong("until", 100);
        batches.add(batch); record.put("batches", batches); records.put("weapon", record);
        legacy.put("records", records);
        var store = BloodTransferStore.CODEC.parse(NbtOps.INSTANCE, legacy).getOrThrow();
        assertEquals(1, legacy.getInt("version", 0)); // Input is never migrated in place.
        assertEquals("unknown", reload(store).residueSummary("weapon"));
        assertEquals("life-a", store.sourceSummary("weapon", 1));
        assertEquals(2, store.transfer("weapon", "hands", 2, 4, 1));
        assertEquals("life-a", store.residueSummary("hands"));
        store.add("weapon", "life-a", 1, 100, 1);
        assertEquals("unknown", store.residueSummary("weapon"));
    }

    @Test void summaryAndBatchesStayBoundedAndFutureVersionsAreRejected() {
        var store = new BloodTransferStore();
        for (int i = 0; i < 100; i++) store.add("weapon", "life-" + i, 1, 100, 0);
        assertTrue(store.record("weapon").getListOrEmpty("residue_sources").size()
                <= ForensicConfig.current.transfer.maxSourcesPerHolder);
        assertEquals("mixed", reload(store).residueSummary("weapon"));
        assertEquals(16, store.visible("weapon"));
        var future = new NbtCompound(); future.putInt("version", 3);
        assertThrows(IllegalStateException.class,
                () -> BloodTransferStore.CODEC.parse(NbtOps.INSTANCE, future).getOrThrow());
        var corrupt = new NbtCompound(); corrupt.putString("records", "broken");
        assertThrows(IllegalStateException.class,
                () -> BloodTransferStore.CODEC.parse(NbtOps.INSTANCE, corrupt).getOrThrow());
        for (int version : new int[]{1, 2}) {
            var invalid = new NbtCompound(); invalid.putInt("version", version);
            var records = new NbtCompound(); records.putString("weapon", "broken");
            invalid.put("records", records);
            assertThrows(IllegalStateException.class,
                    () -> BloodTransferStore.CODEC.parse(NbtOps.INSTANCE, invalid).getOrThrow());
        }
    }
}
