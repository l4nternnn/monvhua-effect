package com.kuilunfuzhe.monvhua.features.area_tip;

import com.kuilunfuzhe.monvhua.features.gravity.GravityAreaSpec;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class AreaTipSpatialIndexTest {
    private static final UUID GROUP = UUID.randomUUID();
    private static AreaTipAreaStore.StoredArea bounds(int x, int y, int z, int dx, int dy, int dz) {
        return new AreaTipAreaStore.StoredArea(UUID.randomUUID(), GROUP, BlockPos.ORIGIN, 1, 0,
                1, 1, 1, -1, new BlockPos(x, y, z), new BlockPos(x + dx, y + dy, z + dz));
    }
    private static boolean matches(AreaTipAreaStore.StoredArea area, Box box) {
        if (area.contains(box.getCenter())) return true;
        for (double x : new double[]{box.minX, box.maxX})
            for (double y : new double[]{box.minY, box.maxY})
                for (double z : new double[]{box.minZ, box.maxZ})
                    if (area.contains(new Vec3d(x, y, z))) return true;
        return false;
    }
    @Test void randomizedQueriesMatchOriginalLinearOrderAndGeometry() {
        Random random = new Random(1783);
        var store = new AreaTipAreaStore();
        assertTrue(store.firstContaining(new Box(0, 0, 0, 1, 1, 1)).isEmpty());
        for (int i = 0; i < 800; i++) {
            int x = random.nextInt(200) - 100, y = random.nextInt(60) - 30, z = random.nextInt(200) - 100;
            if (i % 3 == 0) store.add(bounds(x, y, z, random.nextInt(15), random.nextInt(15), random.nextInt(15)));
            else store.add(new AreaTipAreaStore.StoredArea(UUID.randomUUID(), GROUP, new BlockPos(x, y, z),
                    i % 3, i % 3, 3, 6, 5, -1));
        }
        var areas = store.toAreas();
        for (int i = 0; i < 2500; i++) {
            double x = random.nextInt(230) - 115, y = random.nextInt(80) - 40, z = random.nextInt(230) - 115;
            Box box = new Box(x, y, z, x + 0.6, y + 1.8, z + 0.6);
            assertEquals(areas.stream().filter(a -> matches(a, box)).findFirst(), store.firstContaining(box));
        }
    }
    @Test void mutationsInvalidateIndexAndPreservePriority() {
        var store = new AreaTipAreaStore();
        var first = store.add(bounds(0, 0, 0, 10, 10, 10));
        Box query = new Box(2, 2, 2, 2.6, 3.8, 2.6);
        assertEquals(first, store.firstContaining(query).orElseThrow());
        store.add(bounds(0, 0, 0, 10, 10, 10));
        assertEquals(first, store.firstContaining(query).orElseThrow());
        var latest = store.addLatest(bounds(1, 1, 1, 3, 4, 3));
        assertEquals(latest, store.firstContaining(query).orElseThrow());
        store.removeIntersecting(GROUP, new BlockPos(0, 0, 0), new BlockPos(20, 20, 20));
        assertTrue(store.firstContaining(query).isEmpty());
        store.add(bounds(2, 2, 2, 0, 0, 0));
        Box single = new Box(2.1, 2.1, 2.1, 2.2, 2.2, 2.2);
        assertTrue(store.firstContaining(single).isPresent());
        store.removeIntersectingBlocks(GROUP, List.of(new BlockPos(2, 2, 2)));
        assertTrue(store.firstContaining(single).isEmpty());
        store.add(bounds(2, 2, 2, 1, 1, 1));
        assertTrue(store.firstContaining(single).isPresent());
        store.removeGroupsNotIn(Set.of(UUID.randomUUID()));
        assertTrue(store.firstContaining(single).isEmpty());
    }
    @Test void hugeBoundsDoNotExpandAndDenseQueriesPruneCandidates() {
        var huge = bounds(-29_000_000, -100, -29_000_000, 58_000_000, 400, 58_000_000);
        Box query = new Box(-0.3, 0, -0.3, 0.3, 1.8, 0.3);
        assertEquals(huge, new AreaTipSpatialIndex(List.of(huge)).first(query, a -> matches(a, query)).orElseThrow());
        List<AreaTipAreaStore.StoredArea> dense = new ArrayList<>();
        for (int x = 0; x < 40; x++) for (int z = 0; z < 40; z++) dense.add(bounds(x * 2, 0, z * 2, 0, 2, 0));
        Box target = new Box(78.1, 0.1, 78.1, 78.7, 1.9, 78.7);
        AtomicInteger candidates = new AtomicInteger();
        assertEquals(dense.getLast(), new AreaTipSpatialIndex(dense).first(target, a -> {
            candidates.incrementAndGet(); return matches(a, target);
        }).orElseThrow());
        assertTrue(candidates.get() < 16, "Narrow phase candidates: " + candidates.get());
        System.out.println("AreaTip dense query: " + candidates.get() + " / " + dense.size() + " narrow phase candidates");
    }
    @Test void blockSetsAndInclusiveFacesAreNotLostByBroadPhase() {
        var store = new AreaTipAreaStore();
        var area = store.add(new AreaTipAreaStore.StoredArea(UUID.randomUUID(), GROUP, BlockPos.ORIGIN,
                GravityAreaSpec.Shape.BOX.ordinal(), 0, 1, 1, 1, -1,
                BlockPos.ORIGIN, BlockPos.ORIGIN, List.of(new BlockPos(-20, -10, -30))));
        assertEquals(area, store.firstContaining(new Box(-19.9, -9.9, -29.9, -19.8, -9.8, -29.8)).orElseThrow());
        var face = new AreaTipAreaStore();
        face.add(bounds(0, 0, 0, 0, 0, 0));
        assertTrue(face.firstContaining(new Box(1, 0, 0, 1.5, 0.5, 0.5)).isPresent());
    }
}
