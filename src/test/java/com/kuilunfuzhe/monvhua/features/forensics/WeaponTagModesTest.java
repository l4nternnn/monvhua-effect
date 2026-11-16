package com.kuilunfuzhe.monvhua.features.forensics;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WeaponTagModesTest {
    @Test void singleTagClassifiesExactlyOneKindAndControlsBleeding(){
        for(String kind:List.of("cut","puncture","blunt","burn","other")){
            var selected=WeaponProfiles.selectTags(List.of(kind),"","melee");
            assertNotNull(selected);assertEquals("default",selected.mode());
            assertEquals(List.of(new WeaponProfiles.Component(kind,1)),selected.definition().components());
            assertEquals(kind.equals("cut")||kind.equals("puncture"),selected.definition().externalBleeding());
            assertNull(WeaponProfiles.selectTags(List.of(kind),"","projectile"));
        }
    }
    @Test void multipleTagsRequireAnAllowedModeRatherThanInventingMixedDamage(){
        var kinds=List.of("blunt","cut","puncture");
        assertNull(WeaponProfiles.selectTags(kinds,"","melee"));
        assertNull(WeaponProfiles.selectTags(kinds,"burn","melee"));
        for(String kind:kinds){var selected=WeaponProfiles.selectTags(kinds,kind,"melee");assertNotNull(selected);assertEquals(kind,selected.mode());assertEquals(1,selected.definition().components().size());assertEquals(kind,selected.definition().components().getFirst().kind());}
        assertNull(WeaponProfiles.selectTags(List.of("unknown","invented"),"unknown","melee"));
        assertNull(WeaponProfiles.selectTags(List.of(),"cut","melee"));
    }
    @Test void tagModesCycleInStableOrderAndRecoverAfterTagReload(){
        var kinds=List.of("blunt","puncture","cut");
        assertEquals("cut",WeaponProfiles.nextTagMode(kinds,""));
        assertEquals("puncture",WeaponProfiles.nextTagMode(kinds,"cut"));
        assertEquals("blunt",WeaponProfiles.nextTagMode(kinds,"puncture"));
        assertEquals("cut",WeaponProfiles.nextTagMode(kinds,"blunt"));
        assertEquals("cut",WeaponProfiles.nextTagMode(kinds,"removed_mode"));
        assertEquals("burn",WeaponProfiles.nextTagMode(List.of("burn"),"burn"));
        assertEquals("",WeaponProfiles.nextTagMode(List.of("unknown"),""));
        // Removing a capability invalidates that choice; it never forces a now-unsupported type.
        assertNull(WeaponProfiles.selectTags(List.of("cut","blunt"),"puncture","melee"));
    }
}
