package com.kuilunfuzhe.monvhua.features.physics_preview;
import com.kuilunfuzhe.monvhua.features.forensics.*;
import com.kuilunfuzhe.monvhua.features.forensics.physics.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.util.math.*;
import net.minecraft.util.Hand;
import net.minecraft.item.ItemStack;
import java.util.*;
import static com.kuilunfuzhe.monvhua.features.physics_preview.PhysicsPreviewChecks.require;

final class CorpseTransportGameChecks {
    private static UUID id,player;private static net.minecraft.nbt.NbtCompound saved;
    private static Vec3d position(String part){var e=CorpseHistoryController.get(id);var p=e.session.frames().getLast().poses().get(part);return e.view.origin().add(p.x(),p.y(),p.z());}
    private static void settle(ClientGameTestContext c){c.waitFor(client->{var e=CorpseHistoryController.get(id);require(e!=null,"Transport lost physics: "+CorpsePhysicsCoordinator.status());return e.session.residentState().phase().equals("SLEEPING");},400);c.waitTicks(8);}
    private static void aim(TestSingleplayerContext world,String part){world.getServer().runOnServer(server->{
        var p=server.getPlayerManager().getPlayer(player);p.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);p.setStackInHand(Hand.OFF_HAND,ItemStack.EMPTY);p.getAbilities().flying=true;p.sendAbilitiesUpdate();
        var v=position(part);p.requestTeleport(v.x,v.y+1.1,v.z);p.lookAt(net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES,v);p.bodyYaw=p.getYaw();
    });}
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        world.getServer().runCommand("gamemode creative @a");world.getServer().runCommand("tp @a 0.5 80 3.5 180 20");world.getServer().runCommand("mvcorpse physics mode continuous");
        context.runOnClient(c->c.getNetworkHandler().sendChatCommand("mvcorpse physics test-motion front"));context.waitTicks(5);
        world.getServer().runOnServer(server->{id=CorpsePhysicsCoordinator.liveViews().getFirst().display();player=server.getPlayerManager().getPlayerList().getFirst().getUuid();});settle(context);
        for(String part:CorpseTransportController.LIMBS){
            aim(world,part);
            world.getServer().runOnServer(server->{var p=server.getPlayerManager().getPlayer(player);var d=(ItemDisplayEntity)server.getOverworld().getEntity(id);var result=CorpseTransportController.start(p,d.getId(),"drag",part);require(CorpseTransportController.carrying(player),"Drag refused "+part+": "+result);require(CorpseTransportController.part(player).equals(part),"Wrong dragged limb");require(CorpseCarryService.isCarried(d),"Carry exclusion absent");});
            context.waitTicks(16);
            world.getServer().runOnServer(server->{require(CorpseTransportController.carrying(player),"Drag released prematurely "+part);var p=server.getPlayerManager().getPlayer(player);p.requestTeleport(p.getX()+.3,p.getY(),p.getZ());});context.waitTicks(12);
            world.getServer().runOnServer(server->{var p=server.getPlayerManager().getPlayer(player);CorpseCarryService.handle(p,-1,true);require(!CorpseTransportController.carrying(player),"Release retained ownership");});settle(context);
        }
        aim(world,"torso");
        world.getServer().runOnServer(server->{var p=server.getPlayerManager().getPlayer(player);var d=(ItemDisplayEntity)server.getOverworld().getEntity(id);p.setStackInHand(Hand.MAIN_HAND,new ItemStack(net.minecraft.item.Items.STICK));CorpseTransportController.start(p,d.getId(),"carry","torso");require(!CorpseTransportController.carrying(player),"Occupied hands accepted");p.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);});
        // Exercise actual C2S codec and registered receiver for the carry action.
        int[] target={0};double[] before={0};world.getServer().runOnServer(server->{target[0]=server.getOverworld().getEntity(id).getId();before[0]=position("torso").y;});
        context.runOnClient(c->net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new com.kuilunfuzhe.monvhua.network.forensics.ForensicPackets.CarryRequest(target[0],false,"carry","torso")));
        context.waitTicks(4);world.getServer().runOnServer(server->require(CorpseTransportController.mode(player).equals("carry"),"Carry packet did not start"));
        context.waitTicks(65);world.getServer().runOnServer(server->{require(CorpseTransportController.carrying(player),"Carry stopped");require(position("torso").y>before[0]+.5,"Carry did not lift");});context.runOnClient(c->{c.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_BACK);c.player.setPitch(15);});context.waitTicks(4);context.takeScreenshot("physics-g7-carry");context.runOnClient(c->c.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON));
        world.getServer().runOnServer(server->{var e=CorpseHistoryController.get(id);e.manual=true;e.session.setPaused(true);});context.waitTicks(4);
        world.getServer().runOnServer(server->{require(!CorpseTransportController.carrying(player),"Frozen object still follows player");var e=CorpseHistoryController.get(id);require(e.session.paused(),"Freeze lost");e.manual=false;e.session.setPaused(false);});settle(context);
        aim(world,"torso");world.getServer().runOnServer(server->{var p=server.getPlayerManager().getPlayer(player);var d=(ItemDisplayEntity)server.getOverworld().getEntity(id);String result=CorpseTransportController.start(p,d.getId(),"drag","torso");require(CorpseTransportController.LIMBS.contains(CorpseTransportController.part(player)),"Torso automatic limb failed: "+result);p.setStackInHand(Hand.MAIN_HAND,new ItemStack(net.minecraft.item.Items.STICK));});context.waitTicks(3);
        world.getServer().runOnServer(server->require(!CorpseTransportController.carrying(player),"Changed hands retained carry"));settle(context);
        world.getServer().runOnServer(server->{var d=(ItemDisplayEntity)server.getOverworld().getEntity(id);require(!CorpsePhysicsCoordinator.pending(d),"Released body remains locked");saved=CorpsePoseRecord.data(d.getItemStack()).getCompoundOrEmpty("corpse_pose").copy();});
        context.takeScreenshot("physics-g7-released");
        try{java.nio.file.Files.writeString(PhysicsPreviewClient.instance.directory().getParent().resolve("physics-g7-test.txt"),"PASS four limb drag/release, carry C2S, lift, freeze/detach, occupied hands, automatic limb selection and settled commit\n");}catch(java.io.IOException e){throw new AssertionError(e);}
    }
    static void verifyReload(ClientGameTestContext c,TestSingleplayerContext w){c.waitTicks(10);w.getServer().runOnServer(server->{var d=(ItemDisplayEntity)server.getOverworld().getEntity(id);require(d!=null&&saved.equals(CorpsePoseRecord.data(d.getItemStack()).getCompoundOrEmpty("corpse_pose")),"Transport pose failed reload");require(!CorpseTransportController.carrying(player)&&!CorpseResidentManager.contains(id),"Reload retained transport/native session");});}
}
