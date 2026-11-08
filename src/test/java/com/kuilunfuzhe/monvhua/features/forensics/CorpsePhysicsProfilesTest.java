package com.kuilunfuzhe.monvhua.features.forensics;

import com.kuilunfuzhe.monvhua.features.forensics.physics.*;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CorpsePhysicsProfilesTest {
    @Test void normalizesOrderingAndKeepsRegistryImmutable(){
        String text=CorpsePhysicsProfiles.example();var r=CorpsePhysicsProfiles.parse(text);
        assertEquals(CorpsePhysicsProfiles.F5,r.profiles().get(r.defaultId()));
        var original=r.profiles().get("corpse_custom_v1");var root=JsonParser.parseString(text).getAsJsonObject();
        var reordered=new JsonObject();reordered.add("profiles",root.get("profiles"));reordered.add("default",root.get("default"));reordered.add("schema",root.get("schema"));
        assertEquals(original.hash(),CorpsePhysicsProfiles.parse(reordered.toString()).profiles().get(original.id()).hash());
        assertThrows(UnsupportedOperationException.class,()->r.profiles().clear());
        var m=original.motion();var changed=new CorpsePhysicsProfiles.Profile(original.id(),original.solver(),new CorpsePhysicsProfiles.Motion(.5f,m.unknownSpeed(),m.maxBaseline(),m.maxSpeed(),m.tilt(),m.angularSpeed()));
        assertNotEquals(original.hash(),changed.hash());
        assertEquals(.4f,original.motion().impactSpeed());
    }
    @Test void rejectsMalformedTypesDuplicatesUnknownAndUnsafeValues(){
        String example=CorpsePhysicsProfiles.example();
        for(String bad:new String[]{"null","[]","{}",example+"{}",example.replace("\"schema\": 1","\"schema\": 1, \"schema\": 1"),
                example.replace("\"schema\": 1","\"schema\": 1, \"unexpected\": 0"),example.replace("\"iterations\": 60","\"iterations\": 60.5"),
                example.replace("\"iterations\": 60","\"iterations\": \"60\""),example.replace("\"linearDamping\": 0.3","\"linearDamping\": 1e999"),
                example.replace("\"maxSpeed\": 0.8","\"maxSpeed\": 0.1"),example.replace("corpse_custom_v1","corpse_f5_v1"),
                example.replace("\"default\": \"corpse_f5_v1\"","\"default\": \"missing\"")," ".repeat(65537)}){
            assertThrows(IllegalArgumentException.class,()->CorpsePhysicsProfiles.parse(bad),bad);
        }
    }
    @Test void configuredMotionIsCapturedByValue(){
        var base=CorpsePhysicsProfiles.F5;
        var custom=new CorpsePhysicsProfiles.Profile("test",base.solver(),new CorpsePhysicsProfiles.Motion(.6f,.2f,0,.8f,1,.7f));
        var known=CorpseInitialMotionResolver.resolve(0,0,1,.1,0,"debug","test","",custom);
        assertEquals(.6,known.vz(),1e-6);assertEquals(0,known.baselineX());assertEquals(1,known.tilt());assertSame(custom,known.profile());
        var unknown=CorpseInitialMotionResolver.resolve(0,0,0,0,0,"unknown","unknown","",custom);assertEquals(.2,unknown.vz(),1e-6);
        assertEquals(.4,CorpseInitialMotionResolver.test(0,"back").vz(),1e-6);
    }
}
