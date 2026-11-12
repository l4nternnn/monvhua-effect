package com.kuilunfuzhe.monvhua.features.forensics;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.*;
import net.minecraft.util.math.*;
import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EvidenceCommandPermissionsTest {
    private static ServerCommandSource source(int permission){return new ServerCommandSource(CommandOutput.DUMMY,Vec3d.ZERO,Vec2f.ZERO,null,permission,"test",Text.literal("test"),null,null);}
    @Test void commandRootMergeNeverGrantsPoseEditingOrAdminPlacementToOrdinaryPlayers(){
        for(boolean evidenceFirst:new boolean[]{true,false}){
            var dispatcher=new CommandDispatcher<ServerCommandSource>();
            if(evidenceFirst){dispatcher.register(EvidenceAnchorNetwork.commands());dispatcher.register(CorpsePoseCommands.commands());}
            else{dispatcher.register(CorpsePoseCommands.commands());dispatcher.register(EvidenceAnchorNetwork.commands());}
            var normal=source(0);var admin=source(2);var root=dispatcher.getRoot().getChild("mvcorpse");
            assertTrue(root.canUse(normal));
            for(String name:new String[]{"create-test","samples","status","verify","clear","apply"}){
                assertFalse(root.getChild(name).canUse(normal),name);assertTrue(root.getChild(name).canUse(admin),name);
            }
            var evidence=root.getChild("evidence");assertTrue(evidence.canUse(normal));
            for(String name:new String[]{"place","place_board","remove","clear_all","status"})assertTrue(evidence.getChild(name).canUse(normal),name);
            for(String name:new String[]{"place_at","place_board_at","for","clear_server"}){assertFalse(evidence.getChild(name).canUse(normal),name);assertTrue(evidence.getChild(name).canUse(admin),name);}
        }
    }
}
