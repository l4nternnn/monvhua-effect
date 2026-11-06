package com.kuilunfuzhe.monvhua.features.forensics;

import com.google.gson.JsonParser;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

public class ForensicRegressionTest {
    private static void bootstrap() { SharedConstants.createGameVersion(); Bootstrap.initialize(); }

    @Test public void runtimeMixinsActuallyApply() {
        bootstrap();
        assertTrue(ProjectileEvidence.class.isAssignableFrom(net.minecraft.entity.projectile.ProjectileEntity.class));
        assertTrue(java.util.Arrays.stream(net.minecraft.entity.player.PlayerEntity.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().contains("monvhua$record")), "The post-absorption injury hook must be applied");
    }

    @Test public void shippedSwordSeparatesModes() throws Exception {
        var json = JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/monvhua/forensic_weapon_profiles/sword.json"))).getAsJsonObject();
        var profile = WeaponProfiles.parse("monvhua:sword", json);
        assertEquals("cut", profile.modes().get("slash").components().getFirst().kind());
        assertEquals("puncture", profile.modes().get("thrust").components().getFirst().kind());
        assertEquals("blunt", profile.modes().get("pommel").components().getFirst().kind());
        assertEquals("slash", profile.defaultMode());
        json.addProperty("default_mode", "invented");
        assertThrows(IllegalArgumentException.class, () -> WeaponProfiles.parse("test:invalid", json));
    }

    @Test public void compoundDamageHasOneBudget() {
        var json = JsonParser.parseString("""
          {"schema_version":1,"match":{"items":["minecraft:mace"]},"default_mode":"strike",
           "modes":{"strike":{"delivery":"melee","components":[{"kind":"blunt","weight":0.7},{"kind":"puncture","weight":0.3}]}}}
          """).getAsJsonObject();
        var mode = WeaponProfiles.parse("test:spiked", json).modes().get("strike");
        assertEquals(2, mode.components().size());
        assertEquals(1, mode.components().stream().mapToDouble(WeaponProfiles.Component::weight).sum(), 0.00001);
        json.getAsJsonObject("modes").getAsJsonObject("strike").getAsJsonArray("components").get(1).getAsJsonObject().addProperty("weight", 1);
        assertThrows(IllegalArgumentException.class, () -> WeaponProfiles.parse("test:double_count", json));
    }

    @Test public void persistentLifeDeathAndRespawnStaySeparate() {
        bootstrap();
        ForensicStore store = new ForensicStore();
        UUID player = UUID.randomUUID();
        String lifeId = store.life(player).getString("id", "");
        for (int i = 0; i < 260; i++) {
            NbtCompound hit = new NbtCompound(); hit.putString("id", "hit-" + i); hit.putString("part", "torso"); hit.putString("kinds", "cut");
            store.append(player, hit);
        }
        assertEquals(256, store.life(player).getListOrEmpty("hits").size());
        assertEquals(4, store.life(player).getCompoundOrEmpty("summary").getInt("torso/cut", 0));
        String corpse = store.freeze(player, "victim", "minecraft:overworld", 10, 1, 2, 3, "player");
        assertEquals(corpse, store.freeze(player, "victim", "minecraft:overworld", 11, 1, 2, 3, "player"));
        NbtElement serialized = ForensicStore.CODEC.encodeStart(NbtOps.INSTANCE, store).getOrThrow();
        ForensicStore restored = ForensicStore.CODEC.parse(NbtOps.INSTANCE, serialized).getOrThrow();
        assertEquals(lifeId, restored.life(player).getString("id", ""));
        assertEquals("hit-259", restored.corpse(corpse).getListOrEmpty("hits").getCompound(255).orElseThrow().getString("id", ""));
        restored.newLife(player);
        assertNotEquals(lifeId, restored.life(player).getString("id", ""));
        assertEquals(256, restored.corpse(corpse).getListOrEmpty("hits").size());
        NbtCompound bad = new NbtCompound(); bad.putInt("schema_version", 999);
        assertThrows(IllegalStateException.class, () -> ForensicStore.CODEC.parse(NbtOps.INSTANCE, bad).getOrThrow());
    }

    @Test public void environmentalSourcesNeverBecomeBladeWounds() {
        assertEquals("blunt", Forensics.fallback("fall"));
        assertEquals("burn", Forensics.fallback("on_fire"));
        assertEquals("other", Forensics.fallback("drown"));
        assertEquals("puncture", Forensics.fallback("arrow"));
        assertEquals("unknown", Forensics.fallback("custom_unmapped_magic"));
    }

    @Test public void ordinaryGravityAndAllSixBodyRegions() {
        assertTrue(BodyHitGeometry.normalGravity(null), "No gravity override is ordinary gravity, not an unsupported pose");
        assertTrue(BodyHitGeometry.normalGravity(net.minecraft.util.math.Direction.DOWN));
        assertFalse(BodyHitGeometry.normalGravity(net.minecraft.util.math.Direction.UP));
        assertEquals("head", BodyHitGeometry.part(.9, 0));
        assertEquals("torso", BodyHitGeometry.part(.6, 0));
        assertEquals("left_arm", BodyHitGeometry.part(.6, -.4));
        assertEquals("right_arm", BodyHitGeometry.part(.6, .4));
        assertEquals("left_leg", BodyHitGeometry.part(.2, -.2));
        assertEquals("right_leg", BodyHitGeometry.part(.2, .2));
    }

    @Test public void carpetRejoinCreatesNewLifeButNormalRejoinKeepsInjuries() {
        bootstrap();
        ForensicStore store = new ForensicStore(); UUID player = UUID.randomUUID();
        String firstLife = store.life(player).getString("id", "");
        store.ensureLivingLife(player);
        assertEquals(firstLife, store.life(player).getString("id", ""));
        String firstCorpse = store.freeze(player, "bot", "minecraft:overworld", 1, 0, 0, 0, "player");
        store.life(player).putBoolean("spawn_started", true);
        store.ensureLivingLife(player);
        assertFalse(store.life(player).getBoolean("spawn_started", false));
        String secondCorpse = store.freeze(player, "bot", "minecraft:overworld", 2, 0, 0, 0, "player");
        assertNotEquals(firstCorpse, secondCorpse);
        assertEquals(secondCorpse, store.freeze(player, "bot", "minecraft:overworld", 2, 0, 0, 0, "player"));
        assertFalse(store.corpse(firstCorpse).isEmpty());
    }

    @Test public void strongestThreeAreServerCappedAndSurviveDetailCompaction() {
        bootstrap();
        ForensicStore store = new ForensicStore(); UUID player = UUID.randomUUID();
        for (int i = 0; i < 300; i++) {
            NbtCompound hit = new NbtCompound(); hit.putString("id", "hit-" + i);
            hit.putString("part", i == 1 ? "head" : "torso"); hit.putString("kinds", "blunt");
            hit.putFloat("amount", i < 5 ? 100 - i : 1); store.append(player, hit);
        }
        var selected = InspectionRecords.strongest(store.life(player).getListOrEmpty("inspection_hits"), "torso", 3);
        assertEquals(3, selected.size());
        assertEquals("hit-0", selected.get(0).getString("id", ""));
        assertEquals("hit-2", selected.get(1).getString("id", ""));
        assertEquals("hit-3", selected.get(2).getString("id", ""));
        assertEquals(1, InspectionRecords.strongest(store.life(player).getListOrEmpty("inspection_hits"), "head", 3).size());
    }

    @Test public void wheelHasCarryAtTopAndSixOtherSelectableSectors() {
        assertEquals(-1, CorpseWheelGeometry.sector(0, 0, 30, 100));
        assertEquals(-1, CorpseWheelGeometry.sector(101, 0, 30, 100));
        assertEquals(0, CorpseWheelGeometry.sector(0, -70, 30, 100));
        for (int i = 0; i < 7; i++) {
            double angle = -Math.PI / 2 + i * CorpseWheelGeometry.STEP;
            assertEquals(i, CorpseWheelGeometry.sector(Math.cos(angle) * 70, Math.sin(angle) * 70, 30, 100));
        }
    }

    @Test public void pickupAndPlacementInvalidateCopiedCarriersAfterReload() {
        bootstrap();
        ForensicStore store = new ForensicStore();
        String id = store.freeze(UUID.randomUUID(), "victim", "minecraft:overworld", 10, 0, 0, 0, "player");
        assertTrue(store.claimCarrier(id, "all", "token1", "display1"));
        assertTrue(store.isCarrier(id, "all", "token1", "display1"));
        store.claimCarrier(id, "all", "token2", "item");
        assertFalse(store.isCarrier(id, "all", "token1", "display1"));
        store.claimCarrier(id, "all", "token3", "display2");
        ForensicStore restored = ForensicStore.CODEC.parse(NbtOps.INSTANCE, ForensicStore.CODEC.encodeStart(NbtOps.INSTANCE, store).getOrThrow()).getOrThrow();
        assertFalse(restored.isCarrier(id, "all", "token2", "item"));
        assertTrue(restored.isCarrier(id, "all", "token3", "display2"));
        restored.retireCarrier(id, "all");
        assertFalse(restored.isCarrier(id, "all", "token3", "display2"));
        assertFalse(restored.claimCarrier("unknown", "all", "token", "item"));
    }
}
