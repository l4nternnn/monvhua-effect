package com.kuilunfuzhe.monvhua.features.forensics.cards;

import net.minecraft.util.math.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClueCaptureTargetTest {
    @Test void releasingSneakAfterSelectingFlatGroundDoesNotInvalidateTarget(){
        Vec3d crouchedEye=new Vec3d(.5,2.27,.5),ground=new Vec3d(.5,1,2.5);
        var target=new ClueCaptureTarget(ground,crouchedEye,1200,"overworld");
        Vec3d standingEye=new Vec3d(.5,2.62,.5);
        assertDoesNotThrow(()->target.validate(standingEye,"overworld",20));
        // Re-aiming from the standing eye hits a different spot; confirmation must use the saved ray.
        Vec3d originalDirection=ground.subtract(crouchedEye).normalize();
        double changedDistance=(standingEye.y-1)/-originalDirection.y;
        Vec3d newHit=standingEye.add(originalDirection.multiply(changedDistance));
        assertTrue(newHit.squaredDistanceTo(ground)>.04,"The old current-aim validation rejects this valid grass-ground capture");
        var block=new Box(0,0,2,1,1,3);
        assertTrue(block.raycast(target.eye(),target.point().add(originalDirection.multiply(.02))).isPresent());
    }
    @Test void movingAwayChangingDimensionAndExpiredTargetRemainRejected(){
        var target=new ClueCaptureTarget(new Vec3d(0,0,2),new Vec3d(0,1.5,0),1200,"overworld");
        assertThrows(IllegalArgumentException.class,()->target.validate(new Vec3d(3,1.5,0),"overworld",20));
        assertThrows(IllegalArgumentException.class,()->target.validate(target.eye(),"nether",20));
        assertThrows(IllegalArgumentException.class,()->target.validate(target.eye(),"overworld",1201));
        assertDoesNotThrow(()->target.validate(target.eye(),"overworld",1200));
    }
    @Test void failedValidationDoesNotConsumeTargetAndReturningToPositionAllowsRetry(){
        var target=new ClueCaptureTarget(new Vec3d(0,0,2),new Vec3d(0,1.5,0),1200,"overworld");
        assertThrows(IllegalArgumentException.class,()->target.validate(new Vec3d(8,1.5,0),"overworld",20));
        assertDoesNotThrow(()->target.validate(target.eye(),"overworld",21));
    }
}
