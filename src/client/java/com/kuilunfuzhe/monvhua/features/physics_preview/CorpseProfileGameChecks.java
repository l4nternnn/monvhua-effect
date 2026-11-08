package com.kuilunfuzhe.monvhua.features.physics_preview;

import com.kuilunfuzhe.monvhua.features.forensics.physics.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import java.nio.file.*;
import static com.kuilunfuzhe.monvhua.features.physics_preview.PhysicsPreviewChecks.require;

/** Uses only the isolated game-test run directory. Never invoked in ordinary gameplay. */
final class CorpseProfileGameChecks {
    static void whileFrozen(ClientGameTestContext context,TestSingleplayerContext world){
        context.waitFor(c->!CorpseProfileManager.loading(),100);
        Path file=net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("monvhua/corpse-physics.json");
        String original;
        try{original=Files.readString(file);}catch(java.io.IOException e){throw new AssertionError(e);}
        try{
            Files.writeString(file,CorpsePhysicsProfiles.example());reload(context,world);
            world.getServer().runCommand("mvcorpse physics profile use corpse_custom_v1");
            var saved=CorpseProfileManager.current();require(saved.id().equals("corpse_custom_v1"),"Profile use failed");
            // Same-ID reload replaces the registry without mutating previously captured values.
            Files.writeString(file,CorpsePhysicsProfiles.example().replace("\"impactSpeed\": 0.4","\"impactSpeed\": 0.5"));reload(context,world);
            require(CorpseProfileManager.current().motion().impactSpeed()==.5f,"Reload did not update new-request profile");
            require(saved.motion().impactSpeed()==.4f,"Reload mutated old snapshot");
            var before=CorpseProfileManager.current();
            Files.writeString(file,"{\"schema\":1,\"schema\":1}");reload(context,world);
            require(CorpseProfileManager.current()==before,"Invalid reload replaced valid registry");
            Files.writeString(file,"{\"schema\":1,\"default\":\"corpse_f5_v1\",\"profiles\":[]}");reload(context,world);
            require(CorpseProfileManager.current()==before,"Deleted selected profile was silently replaced");
            world.getServer().runCommand("mvcorpse physics profile use missing_profile");require(CorpseProfileManager.current()==before,"Unknown selection changed state");
        }catch(java.io.IOException e){throw new AssertionError(e);}
        finally{
            world.getServer().runCommand("mvcorpse physics profile use corpse_f5_v1");
            try{Files.writeString(file,original);}catch(java.io.IOException e){throw new AssertionError(e);}
            reload(context,world);
        }
    }
    private static void reload(ClientGameTestContext context,TestSingleplayerContext world){
        world.getServer().runCommand("mvcorpse physics profile reload");context.waitFor(c->!CorpseProfileManager.loading(),100);
    }
}
