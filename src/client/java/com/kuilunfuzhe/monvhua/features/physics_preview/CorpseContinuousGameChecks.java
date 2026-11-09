package com.kuilunfuzhe.monvhua.features.physics_preview;
import com.kuilunfuzhe.monvhua.features.forensics.*;
import com.kuilunfuzhe.monvhua.features.forensics.physics.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.util.math.*;
import net.minecraft.block.Blocks;
import java.util.*;
import static com.kuilunfuzhe.monvhua.features.physics_preview.PhysicsPreviewChecks.require;
final class CorpseContinuousGameChecks {
    private static UUID savedId;private static net.minecraft.nbt.NbtCompound savedPose;
    static void verifyReload(ClientGameTestContext context,TestSingleplayerContext world){context.waitTicks(20);world.getServer().runOnServer(server->{var d=(ItemDisplayEntity)server.getOverworld().getEntity(savedId);require(d!=null&&CorpsePoseRecord.data(d.getItemStack()).getCompoundOrEmpty(CorpsePoseRecord.KEY).equals(savedPose),"Continuous stable pose not restored");require(!CorpseResidentManager.contains(savedId)&&CorpsePhysicsCoordinator.liveViews().isEmpty(),"Reload silently restarted native resident");require(Forensics.valid(server.getOverworld(),d.getItemStack(),d.getUuid().toString()),"Reload lost binding");});}
    private static void aim(net.minecraft.server.network.ServerPlayerEntity player,CorpseHistoryController.Entry entry){var pose=entry.session.frames().getLast().poses().get("torso");var target=entry.view.origin().add(pose.x(),pose.y(),pose.z());player.requestTeleport(target.x,target.y+1,target.z+1);player.lookAt(net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES,target);}
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        world.getServer().runCommand("gamemode creative @a");world.getServer().runCommand("tp @a 0.5 80 3.5 180 20");
        world.getServer().runCommand("mvcorpse physics mode continuous");
        UUID[] id={null};long[] revision={0};
        context.runOnClient(c->c.getNetworkHandler().sendChatCommand("mvcorpse physics test-motion front"));context.waitTicks(5);
        world.getServer().runOnServer(server->{var views=CorpsePhysicsCoordinator.liveViews();require(views.size()==1,"Missing resident view");id[0]=views.getFirst().display();});
        context.waitFor(c->{var e=CorpseHistoryController.get(id[0]);return e!=null&&e.session.residentState().generation()==1&&e.session.residentState().phase().equals("SLEEPING");},240);
        context.waitTicks(5);
        world.getServer().runOnServer(server->{var d=(ItemDisplayEntity)server.getOverworld().getEntity(id[0]);revision[0]=CorpsePoseRecord.data(d.getItemStack()).getLong("corpse_pose_revision",0);require(revision[0]>0&&!CorpsePhysicsCoordinator.pending(d),"Sleeping interactions locked");});
        world.getServer().runCommand("tick freeze");context.waitTicks(4);
        long[] global={0};context.runOnClient(c->global[0]=CorpseHistoryController.get(id[0]).last());context.waitTicks(12);
        context.runOnClient(c->require(CorpseHistoryController.get(id[0]).last()==global[0],"Global tick freeze advanced history"));world.getServer().runCommand("tick unfreeze");
        context.waitTicks(1200); // one minute: initial fall must be gone
        context.runOnClient(c->{var e=CorpseHistoryController.get(id[0]);require(e.last()>3500&&e.first()>e.last()-300&&e.steps().size()<=61,"Sleep history not rolling");});
        var frozen=new long[1];
        world.getServer().runOnServer(server->{var e=CorpseHistoryController.get(id[0]);var player=server.getPlayerManager().getPlayerList().getFirst();player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,new net.minecraft.item.ItemStack(CorpseHistoryController.ITEM));aim(player,e);CorpseHistoryController.operate(player,id[0],false);});context.waitTicks(5);
        world.getServer().runOnServer(server->{var player=server.getPlayerManager().getPlayerList().getFirst();aim(player,CorpseHistoryController.get(id[0]));CorpseHistoryController.operate(player,id[0],true);});
        context.runOnClient(c->{var e=CorpseHistoryController.get(id[0]);c.setScreen(new CorpseTimelineScreen(e,c.getServer(),c.player.getUuid()));});context.waitTicks(3);
        context.runOnClient(c->{var e=CorpseHistoryController.get(id[0]);c.currentScreen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_HOME,0,0);require(e.selectedFrame==e.first()&&e.pose()!=null,"Long timeline cannot pick oldest sample");c.currentScreen.close();});context.waitTicks(3);
        context.runOnClient(c->require(CorpseHistoryController.get(id[0]).manual,"Timeline cleared manual freeze"));
        context.runOnClient(c->frozen[0]=CorpseHistoryController.get(id[0]).last());
        world.getServer().runOnServer(server->{
            for(BlockPos p:BlockPos.iterate(-4,78,-4,4,78,4))server.getOverworld().setBlockState(p,Blocks.STONE.getDefaultState());
            for(BlockPos p:BlockPos.iterate(-4,79,-4,4,79,4))server.getOverworld().setBlockState(p,Blocks.AIR.getDefaultState());
        });context.waitTicks(25);
        context.runOnClient(c->{var e=CorpseHistoryController.get(id[0]);require(e.last()==frozen[0],"Frozen dirty body moved");});
        world.getServer().runOnServer(server->{var player=server.getPlayerManager().getPlayerList().getFirst();aim(player,CorpseHistoryController.get(id[0]));CorpseHistoryController.operate(player,id[0],false);});
        context.waitFor(c->{var e=CorpseHistoryController.get(id[0]);return e!=null&&e.session.residentState().generation()==2&&e.session.residentState().phase().equals("SLEEPING");},240);context.waitTicks(5);
        world.getServer().runOnServer(server->{var d=(ItemDisplayEntity)server.getOverworld().getEntity(id[0]);var data=CorpsePoseRecord.data(d.getItemStack());require(data.getLong("corpse_pose_revision",0)==revision[0]+1,"Wake revision wrong");require(CorpsePoseGeometry.supported(server.getOverworld(),d,data,d.getPos()),"New pose unsupported");});
        context.takeScreenshot("physics-g5-woken");
        // Opt-in ten minute wall-clock soak; each wait remains interruptible by the test harness.
        if(Boolean.getBoolean("monvhua.physicsG5Soak"))for(int i=0;i<9;i++)context.waitTicks(1200);
        world.getServer().runOnServer(server->{var d=(ItemDisplayEntity)server.getOverworld().getEntity(id[0]);require(d!=null&&CorpseResidentManager.contains(id[0]),"Resident vanished");var player=server.getPlayerManager().getPlayerList().getFirst();player.setPosition(d.getPos().add(0,0,1));player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,net.minecraft.item.ItemStack.EMPTY);player.setStackInHand(net.minecraft.util.Hand.OFF_HAND,net.minecraft.item.ItemStack.EMPTY);String carry=CorpseCarryService.handle(player,d.getId(),false);require(CorpseCarryService.isCarried(d),"Sleeping carry failed: "+carry);require(!CorpseResidentManager.contains(id[0]),"Carry retained resident");CorpseCarryService.handle(player,d.getId(),true);require(!CorpseCarryService.isCarried(d),"Drop failed");d.discard();
            for(BlockPos p:BlockPos.iterate(-4,79,-4,4,79,4))server.getOverworld().setBlockState(p,Blocks.STONE.getDefaultState());});
        // Real death uses the same persistent lifecycle, without replaying death on wake.
        world.getServer().runCommand("mvcorpse physics auto on");world.getServer().runCommand("tag @a add dead_body");world.getServer().runCommand("gamemode survival @a");world.getServer().runCommand("tp @a 0.5 80 0.5 0 0");context.waitTicks(5);
        world.getServer().runCommand("kill @a");context.waitTicks(3);
        UUID[] real={null};String[] caseId={null};int[] cases={0};net.minecraft.nbt.NbtCompound[] initial={null},wounds={null};
        world.getServer().runOnServer(server->{var p=server.getPlayerManager().getPlayerList().getFirst();caseId[0]=ForensicStore.get(server).life(p.getUuid()).getString("corpse","");real[0]=CorpsePhysicsCoordinator.liveViews().stream().filter(v->p.getUuid().equals(v.victim())).findFirst().orElseThrow().display();});
        context.runOnClient(c->c.player.requestRespawn());context.waitTicks(10);world.getServer().runCommand("gamemode creative @a");world.getServer().runCommand("tp @a 0.5 82 3.5 180 30");
        context.waitFor(c->{var e=CorpseHistoryController.get(real[0]);require(e!=null,"Real death lost history: "+CorpsePhysicsCoordinator.status());return e.session.residentState().generation()==1&&!CorpsePhysicsCoordinator.liveViews().stream().anyMatch(v->v.display().equals(real[0]));},240);
        world.getServer().runOnServer(server->{var d=(ItemDisplayEntity)server.getOverworld().getEntity(real[0]);cases[0]=ForensicStore.get(server).corpseSnapshots().size();initial[0]=ForensicStore.get(server).corpse(caseId[0]).getCompoundOrEmpty("initial_generated_pose").copy();wounds[0]=CorpsePoseRecord.data(d.getItemStack()).copy();for(BlockPos p:BlockPos.iterate(-4,79,-4,4,79,4))server.getOverworld().setBlockState(p,Blocks.AIR.getDefaultState());});
        context.waitFor(c->{var e=CorpseHistoryController.get(real[0]);return e!=null&&e.session.residentState().generation()==2&&!CorpsePhysicsCoordinator.liveViews().stream().anyMatch(v->v.display().equals(real[0]));},240);
        world.getServer().runOnServer(server->{var d=(ItemDisplayEntity)server.getOverworld().getEntity(real[0]);var store=ForensicStore.get(server);require(initial[0].equals(store.corpse(caseId[0]).getCompoundOrEmpty("initial_generated_pose")),"Wake overwrote original case pose");require(Objects.equals(wounds[0].get("corpse_wounds"),CorpsePoseRecord.data(d.getItemStack()).get("corpse_wounds")),"Wake changed wounds");require(cases[0]==store.corpseSnapshots().size()&&caseId[0].equals(Forensics.binding(d.getItemStack()).getString("id","")),"Wake generated another death");savedId=real[0];savedPose=CorpsePoseRecord.data(d.getItemStack()).getCompoundOrEmpty(CorpsePoseRecord.KEY).copy();});
        try{java.nio.file.Files.writeString(PhysicsPreviewClient.instance.directory().getParent().resolve("physics-g5-test.txt"),"PASS continuous sleep, 60s rolling history, frozen dirty terrain, support removal/re-settle, static revisions, carry disposal, actual death/wake facts; soak="+Boolean.getBoolean("monvhua.physicsG5Soak")+"\n");}catch(java.io.IOException e){throw new AssertionError(e);}
    }
}
