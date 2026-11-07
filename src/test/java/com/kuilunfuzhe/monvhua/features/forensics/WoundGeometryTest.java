package com.kuilunfuzhe.monvhua.features.forensics;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.entity.decoration.InteractionEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WoundGeometryTest {
    @Test void raysSelectNearestSurfaceAndFace() {
        var front = WoundGeometry.hit(new Vec3d(0,3,-20), new Vec3d(0,3,20));
        assertEquals("body_upper",front.getString("surface", ""));
        assertEquals("NORTH",front.getString("face", ""));
        assertEquals(.5, front.getDouble("u", -1), 1e-6);
        assertEquals(.5, front.getDouble("v", -1), 1e-6);
        assertEquals("SOUTH",WoundGeometry.hit(new Vec3d(0,3,20),new Vec3d(0,3,-20)).getString("face", ""));
        assertEquals("head",WoundGeometry.hit(new Vec3d(0,-4,-20),new Vec3d(0,-4,20)).getString("part", ""));
        assertEquals("left_arm",WoundGeometry.hit(new Vec3d(20,3,0),new Vec3d(-20,3,0)).getString("part", ""));
        assertEquals("right_leg",WoundGeometry.hit(new Vec3d(-2,21,-20),new Vec3d(-2,21,20)).getString("part", ""));
        assertTrue(WoundGeometry.hit(new Vec3d(20,-20,0),new Vec3d(20,30,0)).isEmpty());
    }

    @Test void yawTranslationAndScaleKeepSameSurfaceCoordinates() {
        Vec3d feet = new Vec3d(30,60,-20);
        assertEquals(new Vec3d(0,8,-16), WoundGeometry.local(feet.add(0,2,2),feet,0,2));
        Vec3d rotated = WoundGeometry.local(feet.add(-2,2,0),feet,90,2);
        assertEquals(0,rotated.x,1e-6); assertEquals(8,rotated.y,1e-6); assertEquals(-16,rotated.z,1e-6);
    }

    @Test void bentForearmMovesButUpperArmDoesNot() {
        var upper=WoundGeometry.SURFACES.stream().filter(s->s.name().equals("left_arm_upper")).findFirst().orElseThrow();
        var lower=WoundGeometry.SURFACES.stream().filter(s->s.name().equals("left_arm_lower")).findFirst().orElseThrow();
        NbtCompound data=new NbtCompound(); data.putFloat("bend_left_arm_pitch",90);
        Vector3f point=new Vector3f(5f/16,12f/16,0);
        var moved=CorpseSurfacePose.transform(lower,data).transformPosition(new Vector3f(point));
        assertEquals(6f/16,moved.y,1e-6); assertEquals(6f/16,moved.z,1e-6);
        assertEquals(point,CorpseSurfacePose.transform(upper,data).transformPosition(new Vector3f(point)));
    }

    @Test void collisionMixinAndDisplayAccessorApplyAtRuntime() {
        SharedConstants.createGameVersion(); Bootstrap.initialize();
        assertTrue(CorpseBounds.class.isAssignableFrom(InteractionEntity.class));
        assertTrue(com.kuilunfuzhe.monvhua.mixin.forensics.CorpseDisplayAccessor.class
                .isAssignableFrom(net.minecraft.entity.decoration.DisplayEntity.class));
    }

    @Test void fullBodyBoundsRotateAndScaleInsteadOfRemainingHeadSized() {
        var standing=CorpseGeometrySync.bounds(new org.joml.Matrix4f(),new NbtCompound());
        assertEquals(2.08,standing.getLengthY(),1e-5);
        assertEquals(1.08,standing.getLengthX(),1e-5);
        var lying=CorpseGeometrySync.bounds(new org.joml.Matrix4f().rotateX((float)Math.PI/2).scale(2),new NbtCompound());
        assertEquals(4.08,lying.getLengthZ(),1e-5);
        assertEquals(1.08,lying.getLengthY(),1e-5);
        assertNull(CorpseGeometrySync.bounds(new org.joml.Matrix4f().scale(Float.NaN),new NbtCompound()));
    }
}
