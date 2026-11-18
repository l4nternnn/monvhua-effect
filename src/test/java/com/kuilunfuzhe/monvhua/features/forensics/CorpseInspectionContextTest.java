package com.kuilunfuzhe.monvhua.features.forensics;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CorpseInspectionContextTest {
    @Test void standingAfterOpeningDoesNotReplaceCrouchedRayOrSelectedPart(){
        var target=UUID.randomUUID();var feet=new Vec3d(2,64,3);var crouched=feet.add(0,1.27,0);
        var direction=new Vec3d(0,-.5,1).normalize();
        var selection=new CorpseInspectionContext.Selection(target,"left_arm","minecraft:overworld",feet,crouched,direction,600);
        // Selection validation uses feet, which do not move when the screen releases sneak.
        assertTrue(selection.matches(target,"left_arm","minecraft:overworld",feet,40));
        assertEquals(crouched,selection.eye());assertNotEquals(feet.add(0,1.62,0),selection.eye());
        assertEquals(direction,selection.direction());
        assertFalse(selection.matches(target,"torso","minecraft:overworld",feet,40));
        assertFalse(selection.matches(UUID.randomUUID(),"left_arm","minecraft:overworld",feet,40));
        assertFalse(selection.matches(target,"left_arm","minecraft:the_nether",feet,40));
        assertFalse(selection.matches(target,"left_arm","minecraft:overworld",feet.add(1,0,0),40));
        assertFalse(selection.matches(target,"left_arm","minecraft:overworld",feet,601));
    }
}
