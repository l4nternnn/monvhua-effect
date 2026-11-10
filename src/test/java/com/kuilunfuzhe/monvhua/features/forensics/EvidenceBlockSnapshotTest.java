package com.kuilunfuzhe.monvhua.features.forensics;

import net.minecraft.util.math.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EvidenceBlockSnapshotTest {
    @Test void radiusThreeUsesBlockCentersAndNotACube() {
        Vec3d center=new Vec3d(.5,.5,.5);
        var positions=EvidenceBlockSnapshot.positions(center);
        assertEquals(123,positions.size());
        assertTrue(positions.contains(new BlockPos(1,1,0)));
        assertTrue(positions.contains(new BlockPos(1,1,1)));
        assertTrue(positions.contains(new BlockPos(3,0,0)));
        assertTrue(positions.contains(new BlockPos(2,2,1)));
        assertFalse(positions.contains(new BlockPos(2,2,2)));
        assertFalse(positions.contains(new BlockPos(4,0,0)));
        for(var pos:positions)assertTrue(Vec3d.ofCenter(pos).squaredDistanceTo(center)<=9.0);
    }
    @Test void negativeAndFractionalOriginsPreserveSphereAndImmutablePositions() {
        Vec3d center=new Vec3d(-20.2,64.1,3.8);
        var positions=EvidenceBlockSnapshot.positions(center);
        assertFalse(positions.isEmpty());assertEquals(positions.size(),new java.util.HashSet<>(positions).size());
        for(var pos:positions)assertTrue(Vec3d.ofCenter(pos).squaredDistanceTo(center)<=9.0+1e-9);
        var shifted=EvidenceBlockSnapshot.positions(center.add(10,0,-5));
        assertEquals(new java.util.HashSet<>(positions.stream().map(p->p.add(10,0,-5)).toList()),new java.util.HashSet<>(shifted));
    }
}
