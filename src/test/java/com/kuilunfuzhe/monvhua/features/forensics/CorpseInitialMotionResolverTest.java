package com.kuilunfuzhe.monvhua.features.forensics;
import com.kuilunfuzhe.monvhua.features.forensics.physics.CorpseInitialMotionResolver;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CorpseInitialMotionResolverTest {
    @Test void fourDirectionsPreserveBodyYawAndUseWorldAxes() {
        for(float yaw:new float[]{0,90,180,270})for(String side:new String[]{"front","back","left","right"}) {
            var m=CorpseInitialMotionResolver.test(yaw,side);
            assertEquals(side,m.incoming());assertEquals(0,Math.IEEEremainder(m.yaw()-yaw,360),1e-5);
            double a=Math.toRadians(yaw),fx=-Math.sin(a),fz=Math.cos(a);
            double along=m.vx()*fx+m.vz()*fz;
            if(side.equals("front"))assertTrue(along<0);if(side.equals("back"))assertTrue(along>0);
        }
    }
    @Test void movementConvertsTicksOnceAndClampsBeforeCombining() {
        var m=CorpseInitialMotionResolver.resolve(0,0,1,.01,0,"melee","source_position","");
        assertEquals(.2,m.baselineX(),1e-6);assertEquals(.2,m.vx(),1e-6);assertFalse(m.clamped());
        var fast=CorpseInitialMotionResolver.resolve(0,1,0,10,0,"melee","source_position","");
        assertEquals(.4,fast.baselineX(),1e-6);assertTrue(Math.hypot(fast.vx(),fast.vz())<=.80001);assertTrue(fast.clamped());
    }
    @Test void invalidOrVerticalDirectionIsExplicitlyUnknown() {
        for(double bad:new double[]{0,Double.NaN,Double.POSITIVE_INFINITY}) {
            var m=CorpseInitialMotionResolver.resolve(90,bad,0,0,0,"projectile","impact_velocity","");
            assertEquals("unknown",m.incoming());assertEquals("unknown",m.quality());assertTrue(Float.isFinite(m.vx()));assertTrue(m.vx()<0);
        }
    }
    @Test void diagonalDirectionIsContinuous() {
        var a=CorpseInitialMotionResolver.resolve(0,1,.999,0,0,"melee","source_position","");
        var b=CorpseInitialMotionResolver.resolve(0,1,1.001,0,0,"melee","source_position","");
        assertTrue(Math.abs(a.vx()-b.vx())<.001);assertTrue(Math.abs(a.vz()-b.vz())<.001);
    }
}
