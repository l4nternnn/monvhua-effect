package com.kuilunfuzhe.monvhua.features.forensics;

import com.google.gson.JsonParser;
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

public class BloodRegressionTest {
    private static void bootstrap() { SharedConstants.createGameVersion(); Bootstrap.initialize(); }
    private static NbtCompound wound(String part, double factor, float damage) {
        NbtCompound hit = new NbtCompound(); hit.putString("id", UUID.randomUUID().toString()); hit.putString("part", part);
        hit.putDouble("bleeding_factor", factor); hit.putFloat("amount", damage); return hit;
    }
    private static BloodPatch patch(String id, BlockPos pos, int revision) {
        return new BloodPatch(id, pos, Direction.UP, .5f, .5f, .3f, 0, 41, revision);
    }
    private static BloodTraceStore.Entry entry(BloodPatch patch) {
        return new BloodTraceStore.Entry(patch, "SECRET_VICTIM_UUID", "SECRET_HIT_ID", "minecraft:stone", true);
    }

    @Test public void externalBleedingDefaultsAndExplicitOverrides() {
        var json = JsonParser.parseString("""
                {"schema_version":1,"match":{"items":["minecraft:mace"]},"default_mode":"strike",
                 "modes":{"strike":{"delivery":"melee","components":[{"kind":"blunt","weight":0.7},{"kind":"puncture","weight":0.3}]}}}
                """).getAsJsonObject();
        var mode = WeaponProfiles.parse("test:weapon", json).modes().get("strike");
        assertTrue(mode.externalBleeding()); assertEquals(.3, mode.bleedingFactor(), 1e-8);
        var definition = json.getAsJsonObject("modes").getAsJsonObject("strike");
        definition.addProperty("external_bleeding", false);
        var selected = new WeaponProfiles.Selection("test:weapon", "strike", WeaponProfiles.parse("test:weapon", json).modes().get("strike"));
        assertEquals(0, BloodRules.factor(selected, "cut"));
        definition.addProperty("external_bleeding", true); definition.addProperty("bleeding_factor", 2);
        assertEquals(2, WeaponProfiles.parse("test:weapon", json).modes().get("strike").bleedingFactor());
        definition.addProperty("bleeding_factor", -1);
        assertThrows(IllegalArgumentException.class, () -> WeaponProfiles.parse("test:weapon", json));
        definition.addProperty("bleeding_factor", 5);
        assertThrows(IllegalArgumentException.class, () -> WeaponProfiles.parse("test:weapon", json));
        assertEquals(0, BloodRules.factor(null, "blunt"));
        assertEquals(0, BloodRules.factor(null, "unknown"));
        assertEquals(1, BloodRules.factor(null, "puncture"));
    }

    @Test public void oneImpactBudgetForCompoundDamageAndFiniteInputs() {
        assertEquals(6, BloodRules.impactCost(10, .3, 120));
        assertEquals(24, BloodRules.impactCost(1000, 4, 120));
        assertEquals(2, BloodRules.impactCost(10, 1, 2));
        assertEquals(0, BloodRules.impactCost(0, 1, 120));
        assertEquals(0, BloodRules.impactCost(Double.NaN, 1, 120));
        assertEquals(0, BloodRules.impactCost(4, Double.POSITIVE_INFINITY, 120));
    }

    @Test public void corpseSharesConserveVolumeAcrossSplitMergeAndReload() {
        bootstrap();
        NbtCompound corpse = new NbtCompound(); NbtCompound ledger = BloodRules.ledger(corpse);
        BloodRules.remember(ledger, wound("unknown", 1, 20), 100);
        BloodRules.freeze(corpse, 100, true);
        int initial = BloodRules.remaining(ledger, "all"); assertEquals(80, initial);
        BloodRules.consume(ledger, "all", 7);
        for (String part : BloodRules.PARTS) BloodRules.consume(ledger, part, 3);
        assertEquals(initial - 7 - 18, BloodRules.remaining(ledger, "all"));
        BloodRules.freeze(corpse, 120, true);
        assertEquals(initial - 25, BloodRules.remaining(ledger, "all"), "replacing or merging never reinitializes balances");
        NbtCompound restored = NbtCompound.CODEC.parse(NbtOps.INSTANCE, NbtCompound.CODEC.encodeStart(NbtOps.INSTANCE, corpse).getOrThrow()).getOrThrow();
        assertEquals(initial - 25, BloodRules.remaining(restored.getCompoundOrEmpty("blood"), "all"));
        BloodRules.consume(restored.getCompoundOrEmpty("blood"), "all", Integer.MAX_VALUE);
        assertEquals(0, BloodRules.remaining(restored.getCompoundOrEmpty("blood"), "all"));
        assertEquals(0, BloodRules.remaining(ledger, "invented"));
    }

    @Test public void recentWoundsDecayAndLegacyCorpsesStayDry() {
        NbtCompound legacy = new NbtCompound(); BloodRules.freeze(legacy, 100, true); assertFalse(legacy.contains("blood"));
        NbtCompound corpse = new NbtCompound(); var ledger = BloodRules.ledger(corpse);
        BloodRules.remember(ledger, wound("torso", 1, 10), 0);
        BloodRules.freeze(corpse, 600, true);
        assertEquals(20, BloodRules.remaining(ledger, "torso"));
        assertTrue(BloodRules.active(ledger, 2999));
        assertFalse(BloodRules.active(ledger, 3000));
        NbtCompound old = new NbtCompound(); var oldLedger = BloodRules.ledger(old);
        BloodRules.remember(oldLedger, wound("torso", 1, 10), 0); BloodRules.freeze(old, 1200, true);
        assertEquals(0, BloodRules.remaining(oldLedger, "all"));
        NbtCompound gravity = new NbtCompound(); var gravityLedger = BloodRules.ledger(gravity);
        BloodRules.remember(gravityLedger, wound("torso", 1, 10), 0); BloodRules.freeze(gravity, 1, false);
        assertEquals(0, BloodRules.remaining(gravityLedger, "all"));
    }

    @Test public void lifeResetDoesNotResetOldCorpseBudgets() {
        bootstrap(); ForensicStore store = new ForensicStore(); UUID player = UUID.randomUUID();
        var ledger = BloodRules.ledger(store.life(player)); ledger.putInt("impact_remaining", 4);
        BloodRules.remember(ledger, wound("torso", 1, 10), 1);
        String id = store.freeze(player, "victim", "minecraft:overworld", 1, 0, 0, 0, "player");
        BloodRules.freeze(store.corpse(id), 1, true);
        BloodRules.consume(store.corpse(id).getCompoundOrEmpty("blood"), "all", 9);
        store.ensureLivingLife(player);
        assertEquals(120, BloodRules.ledger(store.life(player)).getInt("impact_remaining", -1));
        assertEquals(4, store.corpse(id).getCompoundOrEmpty("blood").getInt("impact_remaining", -1));
        assertEquals(31, BloodRules.remaining(store.corpse(id).getCompoundOrEmpty("blood"), "all"));
    }

    @Test public void projectileSnapshotRetainsItsResolvedRule() {
        NbtCompound data = new NbtCompound(); data.putString("profile", "test:bow"); data.putString("mode", "shoot");
        NbtList components = new NbtList(); NbtCompound c = new NbtCompound(); c.putString("kind", "puncture"); c.putDouble("weight", 1); components.add(c);
        data.put("components", components); data.putBoolean("external_bleeding", false); data.putDouble("bleeding_factor", .25);
        var selection = ProjectileEvidence.selection(data.copy());
        assertNotNull(selection); assertFalse(selection.definition().externalBleeding()); assertEquals(.25, selection.definition().bleedingFactor());
        data.remove("external_bleeding"); data.remove("bleeding_factor");
        assertEquals(1, ProjectileEvidence.selection(data).definition().bleedingFactor(), "legacy snapshots use components, not the current held weapon");
    }

    @Test public void traceStoreRoundTripRevisionCapacityAndRegionalClear() {
        bootstrap(); var store = new BloodTraceStore(); BlockPos pos = new BlockPos(3, 4, 5);
        assertTrue(store.submit(entry(patch("one", pos, 1))));
        assertFalse(store.submit(entry(patch("one", pos, 1))));
        assertTrue(store.submit(entry(patch("one", pos, 2))));
        for (int i = 1; i < 64; i++) assertTrue(store.submit(entry(patch("other" + i, pos, 1))));
        assertFalse(store.submit(entry(patch("overflow", pos, 1))));
        assertTrue(store.submit(entry(patch("remote", new BlockPos(100, 4, 5), 1))));
        var restored = BloodTraceStore.CODEC.parse(NbtOps.INSTANCE, BloodTraceStore.CODEC.encodeStart(NbtOps.INSTANCE, store).getOrThrow()).getOrThrow();
        assertEquals(65, restored.size()); assertEquals(64, restored.at(pos).size());
        assertEquals(2, restored.entry("one").patch().revision());
        assertEquals(64, restored.clear(pos, pos, true)); assertEquals(65, restored.size());
        assertEquals(64, restored.clear(pos, pos, false)); assertEquals(1, restored.size());
        assertTrue(restored.at(pos).isEmpty()); assertEquals(1, restored.chunkKeys().size());
    }

    @Test public void surfaceCoordinatesAndInspectionShareTheSameFootprint() {
        for (Direction face : Direction.values()) {
            var p = new BloodPatch("sample", new BlockPos(-2, 7, 3), face, .5f, .5f, .2f, 0, 81, 1);
            assertEquals(0, p.right().dotProduct(p.down()), 1e-9);
            assertEquals(1, p.right().length(), 1e-9); assertEquals(1, p.down().length(), 1e-9);
            assertTrue(p.covers(p.center())); assertFalse(p.covers(p.center().add(p.right().multiply(.3))));
            assertEquals(p, BloodPatch.read(p.write()));
        }
    }

    @Test public void publicPacketsCannotLeakSourceAndStayBelowByteLimit() {
        bootstrap(); RegistryByteBuf buffer = new RegistryByteBuf(Unpooled.buffer(), DynamicRegistryManager.EMPTY);
        try {
            List<BloodPatch> patches = new ArrayList<>();
            for (int i = 0; i < 96; i++) patches.add(entry(patch(UUID.randomUUID().toString(), new BlockPos(i, 1, -1), 1)).patch());
            List<String> removals = new ArrayList<>(); for (int i = 0; i < 256; i++) removals.add(UUID.randomUUID().toString());
            var packet = new BloodTracePackets.Sync("minecraft:overworld", 9, true, patches, removals);
            BloodTracePackets.Sync.CODEC.encode(buffer, packet);
            assertTrue(buffer.readableBytes() < 32 * 1024);
            String raw = buffer.toString(java.nio.charset.StandardCharsets.ISO_8859_1);
            assertFalse(raw.contains("SECRET_VICTIM_UUID")); assertFalse(raw.contains("SECRET_HIT_ID")); assertFalse(raw.contains("minecraft:stone"));
            assertEquals(packet, BloodTracePackets.Sync.CODEC.decode(buffer));
        } finally { buffer.release(); }
    }

    @Test public void supportAndCollisionHooksActuallyApply() {
        bootstrap();
        assertTrue(Arrays.stream(net.minecraft.world.World.class.getDeclaredMethods()).anyMatch(m -> m.getName().contains("bloodSupportChanged")));
        assertTrue(Arrays.stream(net.minecraft.entity.projectile.ProjectileEntity.class.getDeclaredMethods()).anyMatch(m -> m.getName().contains("beforeCollision")));
    }
}
