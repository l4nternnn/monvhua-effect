package com.kuilunfuzhe.monvhua.features.forensics;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.*;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PostmortemWoundsTest {
    private static final Box BOX = new Box(-.25, 0, -.125, .25, .375, .125);
    private static void bootstrap() { SharedConstants.createGameVersion(); Bootstrap.initialize(); }
    private static Vec3d transform(Matrix4f matrix, Vec3d point, Vec3d anchor) {
        Vector3f p = matrix.transformPosition(new Vector3f((float) point.x, (float) point.y, (float) point.z));
        return new Vec3d(p.x, p.y, p.z).add(anchor);
    }
    private static NbtCompound surface() {
        return WoundGeometry.describe(new WoundGeometry.Surface("body_upper", "torso", BOX), new Vec3d(0, .1875, -.125));
    }
    private static NbtCompound wound(float amount) {
        var mode = new WeaponProfiles.Mode("melee", List.of(new WeaponProfiles.Component("cut", .7), new WeaponProfiles.Component("puncture", .3)));
        return PostmortemWounds.record(surface(), new WeaponProfiles.Selection("test:blade", "slash", mode), amount);
    }
    @Test void lyingRotatedAndScaledCorpsesKeepLocalWoundUVs() {
        Vec3d anchor = new Vec3d(200, 70, -300);
        for (Matrix4f matrix : List.of(new Matrix4f(), new Matrix4f().rotateX((float) Math.PI / 2),
                new Matrix4f().translate(.6f, .2f, -.3f).rotateXYZ(.8f, -.7f, 1.4f).scale(1.8f))) {
            var s = new CorpsePoseGeometry.Surface("body_upper", "torso", BOX, matrix);
            Vec3d from = transform(matrix, new Vec3d(0, .1875, -1), anchor), to = transform(matrix, new Vec3d(0, .1875, 1), anchor);
            var hit = CorpsePoseGeometry.woundHit(List.of(s), anchor, from, to);
            assertNotNull(hit); assertEquals("torso", hit.wound().getString("part", ""));
            assertEquals("NORTH", hit.wound().getString("face", ""));
            assertEquals(.5, hit.wound().getDouble("u", -1), 1e-5);
            assertEquals(.5, hit.wound().getDouble("v", -1), 1e-5);
            assertTrue(hit.position().distanceTo(transform(matrix, new Vec3d(0, .1875, -.125), anchor)) < 1e-5);
        }
    }
    @Test void nearestPosedSurfaceWinsAndShortenedWallRayCannotReachCorpse() {
        var near = new CorpsePoseGeometry.Surface("left_arm_lower", "left_arm", BOX, new Matrix4f().translate(0, 0, -1));
        var far = new CorpsePoseGeometry.Surface("body_upper", "torso", BOX, new Matrix4f());
        Vec3d from = new Vec3d(0, .1875, -3), to = new Vec3d(0, .1875, 2);
        var hit = CorpsePoseGeometry.woundHit(List.of(far, near), Vec3d.ZERO, from, to);
        assertNotNull(hit); assertEquals("left_arm", hit.wound().getString("part", ""));
        assertNull(CorpsePoseGeometry.woundHit(List.of(far, near), Vec3d.ZERO, from, new Vec3d(0, .1875, -2)));
        assertNull(CorpsePoseGeometry.woundHit(List.of(far), Vec3d.ZERO, new Vec3d(2, 2, -3), new Vec3d(2, 2, 3)));
    }
    @Test void singularOrInvalidTransformsCannotInventWounds() {
        for (Matrix4f matrix : List.of(new Matrix4f().scale(0), new Matrix4f().scale(Float.NaN)))
            assertNull(CorpsePoseGeometry.woundHit(List.of(new CorpsePoseGeometry.Surface("head", "head", BOX, matrix)), Vec3d.ZERO,
                    new Vec3d(0, .1, -2), new Vec3d(0, .1, 2)));
        assertNull(CorpsePoseGeometry.woundHit(List.of(new CorpsePoseGeometry.Surface("head", "head", BOX, new Matrix4f())), Vec3d.ZERO,
                new Vec3d(0, .1, 0), new Vec3d(0, .1, 2)));
    }
    @Test void postmortemHistoryDoesNotRewriteDeathOriginalWoundsOrBlood() {
        bootstrap(); var store = new ForensicStore(); UUID player = UUID.randomUUID();
        NbtCompound original = wound(3); original.remove("postmortem"); original.putBoolean("death_trigger", true);
        store.append(player, original);
        String id = store.freeze(player, "victim", "minecraft:overworld", 20, 1, 2, 3, "fall");
        NbtCompound blood = new NbtCompound(); blood.putInt("pool_initial", 20); blood.putInt("impact_remaining", 4);
        store.corpse(id).put("blood", blood.copy());
        NbtList frozen = store.corpse(id).getListOrEmpty("hits").copy();
        NbtCompound extra = wound(8); extra.putBoolean("death_trigger", true); extra.putBoolean("potentially_lethal", true);
        assertTrue(store.appendPostmortem(id, extra));
        assertEquals(frozen, store.corpse(id).getListOrEmpty("hits")); assertEquals(blood, store.corpse(id).getCompoundOrEmpty("blood"));
        assertEquals("fall", store.corpse(id).getString("death_cause", "")); assertEquals(20, store.corpse(id).getLong("death_tick", 0));
        var appended = store.corpse(id).getListOrEmpty("postmortem_hits").getCompound(0).orElseThrow();
        assertTrue(appended.getBoolean("postmortem", false)); assertFalse(appended.contains("death_trigger")); assertFalse(appended.contains("potentially_lethal"));
        extra.putFloat("amount", 99); assertEquals(8, appended.getFloat("amount", 0));
        assertEquals(8, InspectionRecords.strongest(store.corpse(id).getListOrEmpty("inspection_hits"), "torso", 3).getFirst().getFloat("amount", 0));
        var restored = ForensicStore.CODEC.parse(NbtOps.INSTANCE, ForensicStore.CODEC.encodeStart(NbtOps.INSTANCE, store).getOrThrow()).getOrThrow();
        restored.newLife(player);
        assertEquals(appended, restored.corpse(id).getListOrEmpty("postmortem_hits").getCompound(0).orElseThrow());
        assertEquals(2, PostmortemWounds.visuals(restored.corpse(id)).size());
    }
    @Test void duplicateUnknownAndOverCapacityWritesAreRejectedWithoutErasingOldEvidence() {
        bootstrap(); var store = new ForensicStore();
        String id = store.freeze(UUID.randomUUID(), "victim", "minecraft:overworld", 20, 1, 2, 3, "fall");
        NbtCompound first = wound(4); assertFalse(store.appendPostmortem("missing", first));
        assertTrue(store.appendPostmortem(id, first)); assertFalse(store.appendPostmortem(id, first));
        for (int i = 1; i < PostmortemWounds.MAX_WOUNDS; i++) assertTrue(store.appendPostmortem(id, wound(i + 1)));
        assertFalse(store.appendPostmortem(id, wound(100)));
        assertEquals(PostmortemWounds.MAX_WOUNDS, store.corpse(id).getListOrEmpty("postmortem_hits").size());
        assertEquals(first.getString("id", ""), store.corpse(id).getListOrEmpty("postmortem_hits").getCompound(0).orElseThrow().getString("id", ""));
    }
    @Test void publicVisualsHaveNoPostmortemIdentityOrTimeMarkers() {
        NbtCompound corpse = new NbtCompound(), record = wound(5); record.putString("attacker", "secret"); record.putLong("tick", 42);
        record.getCompoundOrEmpty("wound_surface").putString("attacker", "injected");
        NbtList hits = new NbtList(); hits.add(record); corpse.put("postmortem_hits", hits);
        NbtCompound visual = PostmortemWounds.visuals(corpse).getCompound(0).orElseThrow();
        assertEquals(Set.of("surface", "part", "face", "u", "v", "kinds", "severity"), visual.getKeys());
        assertEquals("cut+puncture", visual.getString("kinds", "")); assertEquals(2, record.getListOrEmpty("components").size());
        assertFalse(visual.toString().contains("secret")); assertFalse(visual.toString().contains("injected"));
    }
    @Test void attackStrengthUsesCooldownAndRejectsNonfiniteValues() {
        assertEquals(8, PostmortemWounds.damage(8, 1), 1e-5); assertEquals(1.6, PostmortemWounds.damage(8, 0), 1e-5);
        assertEquals(3.2, PostmortemWounds.damage(8, .5f), 1e-5);
        assertEquals(0, PostmortemWounds.damage(Double.NaN, 1)); assertEquals(0, PostmortemWounds.damage(1, Float.NaN));
        assertEquals(0, PostmortemWounds.damage(-1, 1)); assertEquals(1024, PostmortemWounds.damage(1e10, 1));
    }
}
