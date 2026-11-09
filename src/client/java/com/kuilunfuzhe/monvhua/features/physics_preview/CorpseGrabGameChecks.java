package com.kuilunfuzhe.monvhua.features.physics_preview;
import com.kuilunfuzhe.monvhua.features.forensics.physics.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.util.math.*;
import net.minecraft.util.Hand;
import net.minecraft.item.ItemStack;
import java.util.*;
import static com.kuilunfuzhe.monvhua.features.physics_preview.PhysicsPreviewChecks.require;

/** Short integrated authority/render/mixin smoke; manual mouse feel remains a user check. */
final class CorpseGrabGameChecks {
    private static UUID staticId;private static net.minecraft.nbt.NbtCompound staticData;
    static void verifyReload(ClientGameTestContext context,TestSingleplayerContext world){
        context.waitTicks(10);world.getServer().runOnServer(server->{
            var d=(net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity)server.getOverworld().getEntity(staticId);
            require(d!=null,"Static corpse missing after reload");
            var data=com.kuilunfuzhe.monvhua.features.forensics.CorpsePoseRecord.data(d.getItemStack());
            require(staticData.equals(data.getCompoundOrEmpty("corpse_pose")),"Static pose changed after reload");
            require(!CorpseResidentManager.contains(staticId),"Reload restarted static physics");
        });
    }
    private static void verifySlots(ClientGameTestContext context,TestSingleplayerContext world,UUID id,UUID playerId){
        var pinnedArm=new java.util.concurrent.atomic.AtomicReference<PhysicsPoseDocument.Pose>();
        for(String selected:java.util.List.of("right_arm","left_arm")){
            world.getServer().runOnServer(server->{
                var p=server.getPlayerManager().getPlayer(playerId);var e=CorpseHistoryController.get(id);
                var part=e.session.frames().getLast().poses().get(selected);var target=e.view.origin().add(part.x(),part.y(),part.z());
                p.requestTeleport(target.x,target.y+1.1,target.z);p.lookAt(net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES,target);
                var hit=CorpseGrabController.pick(p);require(hit!=null&&hit.part().equals(selected),"Test aim missed limb: "+selected+" hit="+hit);
                CorpseGrabController.toggle(p,true);require(CorpseGrabController.status(playerId)!=null,"Pinned corpse refused another limb");
            });
            context.waitTicks(4);
            world.getServer().runOnServer(server->{
                var e=CorpseHistoryController.get(id);
                require(selected.equals(e.session.dynamicPart()),"Wrong active limb slot");
                if(pinnedArm.get()!=null){var now=e.session.frames().getLast().poses().get("right_arm");var old=pinnedArm.get();require(Math.abs(now.x()-old.x())+Math.abs(now.y()-old.y())+Math.abs(now.z()-old.z())<1e-5&&now.rotation().equals(old.rotation(),1e-5f),"Previous arm moved while editing another limb");}
                CorpseGrabController.freeze(server.getPlayerManager().getPlayer(playerId),true);
                require(CorpseGrabController.status(playerId)==null,"Limb freeze retained grab ownership");
            });
            context.waitFor(c->{var e=CorpseHistoryController.get(id);return e!=null&&e.session.residentState().phase().equals("PINNED");},40);
            world.getServer().runOnServer(server->{var e=CorpseHistoryController.get(id);require(!e.manual&&!e.session.paused(),"Local pin paused whole session");require("none".equals(e.session.dynamicPart()),"Frozen limb still occupies slot");if(selected.equals("right_arm"))pinnedArm.set(e.session.frames().getLast().poses().get(selected));});
        }
        // Pinned bodies qualify for static confirmation, even though their rolling history clock continues.
        world.getServer().runOnServer(server->{var p=server.getPlayerManager().getPlayer(playerId);CorpseGrabController.staticPress(p);require(CorpseResidentManager.contains(id),"First static press converted pinned pose");CorpseGrabController.clearStaticConfirmation(playerId);CorpseGrabController.range(p);});
        context.waitFor(c->{var e=CorpseHistoryController.get(id);return e!=null&&e.session.residentState().phase().equals("SLEEPING");},300);
        context.waitTicks(4);
    }
    private static void verifyStatic(ClientGameTestContext context,TestSingleplayerContext world,UUID id,UUID playerId){
        var before=new java.util.concurrent.atomic.AtomicReference<PhysicsPoseDocument>();
        world.getServer().runOnServer(server->{
            var p=server.getPlayerManager().getPlayer(playerId);var e=CorpseHistoryController.get(id);
            boolean rejected=false;try{CorpseGrabController.staticPress(p);}catch(IllegalArgumentException expected){rejected=true;}require(rejected,"Unfrozen static conversion accepted");
            var t=e.session.frames().getLast().poses().get("torso");var target=e.view.origin().add(t.x(),t.y(),t.z());p.requestTeleport(target.x,target.y+1,target.z+1);p.lookAt(net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES,target);
            CorpseGrabController.freeze(p);
        });
        context.waitFor(c->CorpseHistoryController.get(id).confirmed(),40);
        world.getServer().runOnServer(server->{
            var e=CorpseHistoryController.get(id);before.set(e.session.frames().getLast());
            for(var pos:BlockPos.iterate(-8,79,-8,8,79,8))server.getOverworld().setBlockState(pos,net.minecraft.block.Blocks.AIR.getDefaultState());
        });
        // Key repeat and a second PRESS without RELEASE must not count as a double tap.
        context.runOnClient(c->{CorpseGrabClient.key(org.lwjgl.glfw.GLFW.GLFW_KEY_H,0,org.lwjgl.glfw.GLFW.GLFW_PRESS);CorpseGrabClient.key(org.lwjgl.glfw.GLFW.GLFW_KEY_H,0,org.lwjgl.glfw.GLFW.GLFW_REPEAT);CorpseGrabClient.key(org.lwjgl.glfw.GLFW.GLFW_KEY_H,0,org.lwjgl.glfw.GLFW.GLFW_PRESS);});
        context.waitTicks(2);
        world.getServer().runOnServer(server->require(CorpseResidentManager.contains(id),"Single/held key converted corpse"));
        context.runOnClient(c->{CorpseGrabClient.key(org.lwjgl.glfw.GLFW.GLFW_KEY_H,0,org.lwjgl.glfw.GLFW.GLFW_RELEASE);CorpseGrabClient.key(org.lwjgl.glfw.GLFW.GLFW_KEY_H,0,org.lwjgl.glfw.GLFW.GLFW_PRESS);CorpseGrabClient.key(org.lwjgl.glfw.GLFW.GLFW_KEY_H,0,org.lwjgl.glfw.GLFW.GLFW_RELEASE);});
        context.waitFor(c->!CorpseResidentManager.contains(id),40);context.waitTicks(5);
        world.getServer().runOnServer(server->{
            var d=(net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity)server.getOverworld().getEntity(id);
            var data=com.kuilunfuzhe.monvhua.features.forensics.CorpsePoseRecord.data(d.getItemStack());var record=com.kuilunfuzhe.monvhua.features.forensics.CorpsePoseRecord.read(data).orElseThrow();
            require(record.source().equals("frozen_static")&&data.getCompoundOrEmpty("corpse_pose").getString("state","").equals("STATIC"),"Frozen static state mislabeled");
            // Recover the original local origin from the deterministic test request (0.5,80,3.5).
            var torso=before.get().poses().get("torso");
            for(var part:record.parts().entrySet()){
                var p=part.getValue();var old=before.get().poses().get(part.getKey());
                require(Math.abs(p.x()+Math.floor(torso.x())-old.x())<1e-5&&Math.abs(p.y()+Math.floor(torso.y())-old.y())<1e-5&&Math.abs(p.z()+Math.floor(torso.z())-old.z())<1e-5,"Static conversion changed part position");
                require(p.rotation().equals(old.rotation(),1e-6f),"Static conversion changed rotation");
            }
            require(CorpseHistoryController.get(id)==null&&CorpseGrabController.status(playerId)==null,"Static conversion retained control/history");
            require(!com.kuilunfuzhe.monvhua.features.forensics.CorpsePoseGeometry.supported(server.getOverworld(),d,data,d.getPos()),"Static test was not suspended");
            staticId=id;staticData=data.getCompoundOrEmpty("corpse_pose").copy();
            for(var pos:BlockPos.iterate(-8,79,-8,8,79,8))server.getOverworld().setBlockState(pos,net.minecraft.block.Blocks.STONE.getDefaultState());
        });
        context.takeScreenshot("physics-g6-frozen-static");
    }
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        world.getServer().runCommand("gamemode creative @a");world.getServer().runCommand("tp @a 0.5 80 3.5 180 20");world.getServer().runCommand("mvcorpse physics mode continuous");
        context.runOnClient(c->c.getNetworkHandler().sendChatCommand("mvcorpse physics test-motion front"));context.waitTicks(5);
        UUID[] id={null},playerId={null};
        world.getServer().runOnServer(server->{id[0]=CorpsePhysicsCoordinator.liveViews().getFirst().display();playerId[0]=server.getPlayerManager().getPlayerList().getFirst().getUuid();});
        context.waitFor(c->{var e=CorpseHistoryController.get(id[0]);return e!=null&&e.session.residentState().phase().equals("SLEEPING");},240);context.waitTicks(5);
        world.getServer().runOnServer(server->{var p=server.getPlayerManager().getPlayer(playerId[0]);var e=CorpseHistoryController.get(id[0]);var t=e.session.frames().getLast().poses().get("torso");var target=e.view.origin().add(t.x(),t.y(),t.z());p.requestTeleport(target.x,target.y+1.2,target.z+1);p.lookAt(net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES,target);p.setStackInHand(Hand.MAIN_HAND,new ItemStack(CorpseGrabController.ITEM));CorpseGrabController.toggle(p,false);require(CorpseGrabController.status(p.getUuid())!=null,"Grab rejected");});
        for(int i=0;i<15;i++){world.getServer().runOnServer(server->{var p=server.getPlayerManager().getPlayer(playerId[0]);CorpseGrabController.input(p,-.2,new Vec3d(0,.15,0),true);});context.waitTicks(1);}
        context.runOnClient(c->{
            require(CorpseGrabClient.scroll(1),"Scroll not consumed");
            require(CorpseGrabClient.button(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT,org.lwjgl.glfw.GLFW.GLFW_PRESS),"Rotation button not consumed");
            float yaw=c.player.getYaw(),pitch=c.player.getPitch();c.player.changeLookDirection(10,5);
            require(yaw==c.player.getYaw()&&pitch==c.player.getPitch(),"Rotation input moved camera");
            CorpseGrabClient.button(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT,org.lwjgl.glfw.GLFW.GLFW_RELEASE);
        });
        context.takeScreenshot("physics-g6-grab");
        long[] frozen={0};
        world.getServer().runOnServer(server->CorpseGrabController.freeze(server.getPlayerManager().getPlayer(playerId[0])));context.waitTicks(3);
        context.runOnClient(c->frozen[0]=CorpseHistoryController.get(id[0]).last());context.waitTicks(8);
        context.runOnClient(c->require(CorpseHistoryController.get(id[0]).last()==frozen[0],"Frozen grab advanced"));
        world.getServer().runOnServer(server->{var p=server.getPlayerManager().getPlayer(playerId[0]);CorpseGrabController.release(p.getUuid());CorpseGrabController.freeze(p);});
        // Freeze without a held selection uses picking; explicitly restore the entry if the player moved.
        world.getServer().runOnServer(server->{var e=CorpseHistoryController.get(id[0]);if(e.manual){e.manual=false;e.session.setPaused(false);}});
        context.waitFor(c->{var e=CorpseHistoryController.get(id[0]);require(e!=null,"Grab lost resident: "+CorpsePhysicsCoordinator.status());return e.session.residentState().phase().equals("SLEEPING");},300);context.waitTicks(8);
        world.getServer().runOnServer(server->{require(CorpseGrabController.status(playerId[0])==null,"Released ownership retained");var d=(net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity)server.getOverworld().getEntity(id[0]);require(!CorpsePhysicsCoordinator.pending(d),"Settled interactions still blocked");});
        context.takeScreenshot("physics-g6-released");
        verifySlots(context,world,id[0],playerId[0]);
        verifyStatic(context,world,id[0],playerId[0]);
        try{java.nio.file.Files.writeString(PhysicsPreviewClient.instance.directory().getParent().resolve("physics-g6-test.txt"),"PASS dedicated item, authority pick/grab, distance/rotation updates, freeze, release and settled commit; actual hand-operated mouse feel pending\n");}catch(java.io.IOException e){throw new AssertionError(e);}
    }
}
