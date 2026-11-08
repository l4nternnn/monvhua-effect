package com.kuilunfuzhe.monvhua.features.physics_preview;

import com.kuilunfuzhe.monvhua.features.forensics.*;
import com.kuilunfuzhe.monvhua.features.block.body.BodyPartManager;
import com.kuilunfuzhe.monvhua.renderer.body.special.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.entity.decoration.InteractionEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.nbt.*;
import net.minecraft.util.math.*;
import org.joml.*;
import java.util.*;
import static com.kuilunfuzhe.monvhua.features.physics_preview.PhysicsPreviewChecks.require;

/** Runs only in the existing opt-in disposable client game-test world. */
final class CorpsePoseGameChecks {
    static void run(ClientGameTestContext context, net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext testWorld) {
        int[] ids=new int[2];NbtCompound[] saved={null};Matrix4f[] root={null};
        var expected=new java.util.concurrent.atomic.AtomicReference<List<CorpsePoseGeometry.Surface>>();
        String oldProperty=System.getProperty("monvhua.physicsCorpseDebug");System.setProperty("monvhua.physicsCorpseDebug","true");
        try {
            testWorld.getServer().runOnServer(server->{
                var player=server.getPlayerManager().getPlayerList().getFirst();var source=player.getCommandSource().withLevel(4);
                require(command(server,source,"mvcorpse samples")==1,"Samples command");
                require(command(server,source,"mvcorpse create-test")==1,"Create test carrier");
                var world=server.getOverworld();
                ItemDisplayEntity display=world.getEntitiesByClass(ItemDisplayEntity.class,player.getBoundingBox().expand(8),d->d.getCommandTags().contains("monvhua_physics_test")).getFirst();
                String target=display.getUuid().toString();ids[0]=display.getId();
                var interaction=(InteractionEntity)world.getEntity(BodyPartManager.DISPLAY_TO_INTERACTION.get(display.getUuid()));ids[1]=interaction.getId();
                var n=CorpsePoseRecord.data(display.getItemStack());n.putString("preservation_probe","keep");
                n.put("corpse_wounds",PhysicsPreviewFixtures.marks().getListOrEmpty("corpse_wounds").copy());
                var stack=display.getItemStack().copy();stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,net.minecraft.component.type.NbtComponent.of(n));display.setItemStack(stack);
                require(command(server,source,"mvcorpse apply "+target+" floor.json")==1,"Apply stable floor");
                var data=CorpsePoseRecord.data(display.getItemStack());require(CorpsePoseRecord.read(data).orElseThrow().armModel().equals("slim"),"Default slim");
                require(data.getString("preservation_probe","").equals("keep"),"Component lost");
                require(data.getListOrEmpty("corpse_wounds").equals(n.getListOrEmpty("corpse_wounds")),"Wounds lost");
                var before=display.getItemStack().copy();
                require(command(server,source,"mvcorpse apply "+target+" \"../floor.json\"")==0,"Traversal accepted");
                require(net.minecraft.item.ItemStack.areEqual(before,display.getItemStack()),"Failed import changed item");
                require(!BodyPartManager.updatePlacedCombinedDisplayPose(player,display.getId(),"",null,null,List.of(),0,0,0,0,0,0,1,false),"Legacy editor accepted rigid target");
                var surfaces=CorpsePoseGeometry.surfaces(display,data);var torso=surfaces.stream().filter(s->s.part().equals("torso")).findFirst().orElseThrow();
                var center=CorpsePoseGeometry.bounds(torso).getCenter().add(display.getPos());
                var hit=CorpsePoseGeometry.pick(display,center.add(0,3,0),center.add(0,-1,0));require(hit!=null,"Fine hit failed");
                var block=BlockPos.ofFloored(center);var prior=world.getBlockState(block);world.setBlockState(block,net.minecraft.block.Blocks.STONE.getDefaultState());
                require(!CorpsePoseGeometry.clear(world,display,data,display.getPos()),"Full geometry missed obstacle");
                require(command(server,source,"mvcorpse apply "+target+" floor.json")==0,"Obstructed apply accepted");
                require(net.minecraft.item.ItemStack.areEqual(before,display.getItemStack()),"Obstructed apply mutated state");world.setBlockState(block,prior);
                EvidenceSnapshots.observe(player,display,"torso");String caseId=Forensics.binding(display.getItemStack()).getString("id","");
                var observation=ForensicStore.get(server).observations(player.getUuid()).getCompoundOrEmpty(caseId).copy();
                var pos=display.getPos();display.setPosition(pos.add(1,0,0));CorpseGeometrySync.update(display);
                require(observation.equals(ForensicStore.get(server).observations(player.getUuid()).getCompoundOrEmpty(caseId)),"Observation followed live movement");display.setPosition(pos);
                require(command(server,source,"mvcorpse clear "+target)==1,"Clear failed");
                require(!CorpsePoseRecord.present(display.getItemStack()),"Clear left pose");
                require(command(server,source,"mvcorpse apply "+target+" floor.json")==1,"Reapply failed");
                data=CorpsePoseRecord.data(display.getItemStack());require(data.getLong("corpse_pose_revision",0)==3,"Revision reuse");
                String carry=CorpseCarryService.handle(player,display.getId(),false);
                require(CorpseCarryService.isCarried(display),"Carry start: "+carry);
                require(command(server,source,"mvcorpse clear "+target)==0,"Pose changed while carrying");
                CorpseCarryService.handle(player,display.getId(),true);
                require(!CorpseCarryService.isCarried(display),"Release did not finish");
                require(CorpsePoseGeometry.supported(world,display,CorpsePoseRecord.data(display.getItemStack()),display.getPos()),"Release lacks support");
                // Real ItemStack persistence codec, rather than a hand-built copy of its fields.
                var encoded=net.minecraft.item.ItemStack.CODEC.encodeStart(NbtOps.INSTANCE,display.getItemStack()).getOrThrow();
                var restored=net.minecraft.item.ItemStack.CODEC.parse(NbtOps.INSTANCE,encoded).getOrThrow();
                require(CorpsePoseRecord.data(restored).equals(data),"Item persistence changed pose/components");
                saved[0]=data;root[0]=CorpseGeometrySync.placement(display,data);expected.set(CorpsePoseGeometry.surfaces(display,data));
            });
            context.waitTicks(12);
            context.runOnClient(client->{
                var display=(ItemDisplayEntity)client.world.getEntityById(ids[0]);var interaction=(InteractionEntity)client.world.getEntityById(ids[1]);
                require(display!=null&&interaction!=null,"Tracked entities missing");
                require(CorpsePoseRecord.data(display.getItemStack()).equals(saved[0]),"Tracked pose/components differ");
                var renderer=new CombinedBodySpecialModelRenderer(client.getLoadedEntityModels());
                var surfaces=renderer.evidenceSurfaces(renderer.getData(display.getItemStack()),root[0]);
                for(var surface:surfaces) {
                    var common=expected.get().stream().filter(s->s.name().equals(surface.name())).findFirst().orElseThrow();
                    Box b=surface.box();var actual=new ArrayList<Vec3d>();
                    for(int i=0;i<8;i++){var p=surface.transform().transformPosition(new Vector3f((float)((i&1)==0?b.minX:b.maxX),(float)((i&2)==0?b.minY:b.maxY),(float)((i&4)==0?b.minZ:b.maxZ)));actual.add(new Vec3d(p.x,p.y,p.z));}
                    for(Vec3d point:actual) {
                        require(CorpsePoseGeometry.corners(common).stream().anyMatch(p->p.distanceTo(point)<.002),"Server/client surface mismatch: "+surface.name());
                        require(interaction.getBoundingBox().contains(point.add(display.getPos())),"Tracked interaction misses skin");
                    }
                }
                PhysicsPreviewClient.instance.clear();
            });
            testWorld.getServer().runOnServer(server->{
                var d=(ItemDisplayEntity)server.getOverworld().getEntityById(ids[0]);var player=server.getPlayerManager().getPlayerList().getFirst();
                command(server,player.getCommandSource().withLevel(4),"tp "+player.getName().getString()+" "+d.getX()+" "+(d.getY()+2)+" "+(d.getZ()+4)+" 180 40");
            });
            context.runOnClient(client->client.options.hudHidden=true);
            try {context.waitTicks(4);context.takeScreenshot("physics-real-corpse-slim");}
            finally {context.runOnClient(client->client.options.hudHidden=false);}
            // Exercise the actual client command interception + network + permission path.
            testWorld.getServer().runOnServer(server->{
                var player=server.getPlayerManager().getPlayerList().getFirst();
                server.getPlayerManager().addToOperators(player.getGameProfile());
            });
            context.waitTicks(5);
            context.runOnClient(client->{
                require(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.getActiveDispatcher().getRoot().getChild("mvcorpse")==null,"Server command shadowed by client dispatcher");
                var display=(ItemDisplayEntity)client.world.getEntityById(ids[0]);
                client.getNetworkHandler().sendChatCommand("mvcorpse clear "+display.getUuid());
            });
            context.waitFor(client->!CorpsePoseRecord.present(((ItemDisplayEntity)client.world.getEntityById(ids[0])).getItemStack()),200);
            context.runOnClient(client->{
                var display=(ItemDisplayEntity)client.world.getEntityById(ids[0]);
                client.getNetworkHandler().sendChatCommand("mvcorpse apply "+display.getUuid()+" floor.json");
            });
            context.waitFor(client->CorpsePoseRecord.present(((ItemDisplayEntity)client.world.getEntityById(ids[0])).getItemStack()),200);

        }catch(Exception e){throw new AssertionError("Corpse integration",e);}
        finally{if(oldProperty==null)System.clearProperty("monvhua.physicsCorpseDebug");else System.setProperty("monvhua.physicsCorpseDebug",oldProperty);}
    }
    private static int command(MinecraftServer server,ServerCommandSource source,String command) {
        try{return server.getCommandManager().getDispatcher().execute(command,source);}catch(Exception e){throw new AssertionError(command,e);}
    }
}
