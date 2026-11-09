package com.kuilunfuzhe.monvhua.features.forensics;

import java.util.*;
import net.minecraft.util.math.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class OrientedBoxCollisionTest {
    private List<Vec3d> rod(double angle){var result=new ArrayList<Vec3d>();for(int i=0;i<8;i++){double x=(i&1)==0?-2:2,y=(i&2)==0?-.1:.1,z=(i&4)==0?-.1:.1;result.add(new Vec3d(x*Math.cos(angle)-y*Math.sin(angle),x*Math.sin(angle)+y*Math.cos(angle),z));}return result;}
    @Test void rotatedBroadPhaseFalsePositiveIsClearButCrossingAndContainmentAreNot(){
        var points=rod(Math.PI/4);
        assertFalse(OrientedBoxCollision.overlaps(points,new Box(1.1,-1.3,-.05,1.3,-1.1,.05),.03));
        assertTrue(OrientedBoxCollision.overlaps(points,new Box(-.2,-.2,-.2,.2,.2,.2),.03));
        assertTrue(OrientedBoxCollision.overlaps(points,new Box(-3,-3,-3,3,3,3),.03));
    }
    @Test void unchangedContactToleranceAndTouchingAreClear(){
        var points=rod(0);
        assertFalse(OrientedBoxCollision.overlaps(points,new Box(-3,-1,-1,3,-.1,1),.03));
        assertFalse(OrientedBoxCollision.overlaps(points,new Box(-3,-1,-1,3,-.08,1),.03));
        assertTrue(OrientedBoxCollision.overlaps(points,new Box(-3,-1,-1,3,-.06,1),.03));
    }
}
