package com.kuilunfuzhe.monvhua.features.physics_preview;

import com.kuilunfuzhe.monvhua.features.forensics.*;
import com.kuilunfuzhe.monvhua.features.forensics.physics.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.util.math.*;
import java.util.*;
import static com.kuilunfuzhe.monvhua.features.physics_preview.PhysicsPreviewChecks.require;

/** Real backend and real death path, isolated from the user's saves. */
final class CorpseAutomaticGameChecks {
    static void missing(ClientGameTestContext context,TestSingleplayerContext world) {
        require(CorpsePhysicsCoordinator.status().contains("无本地后端")||CorpsePhysicsCoordinator.status().contains("Unavailable"),"Expected absent/unavailable optional core");
        world.getServer().runOnServer(server->{
            boolean[] closed={false};
            CorpsePhysicsCoordinator.attach(server,new CorpsePhysicsBridge(){
                public int apiVersion(){return API_VERSION-1;}
                public String status(){return "old test bridge";}
                public java.util.concurrent.CompletableFuture<PhysicsPoseDocument> solve(Request request){throw new AssertionError("Old bridge executed");}
                public void close(){closed[0]=true;}
            });
            require(closed[0]&&!CorpsePhysicsCoordinator.localControl(server),"Mismatched bridge was not closed/rejected");
        });
        context.runOnClient(client->client.getNetworkHandler().sendChatCommand("mvcorpse physics auto on"));context.waitTicks(3);
        world.getServer().runCommand("tag @a add dead_body");world.getServer().runCommand("kill @a");context.waitTicks(5);
        world.getServer().runOnServer(server->{
            var corpses=server.getOverworld().getEntitiesByClass(ItemDisplayEntity.class,new Box(-8,78,-8,8,86,12),
                    d->Forensics.hasBinding(d.getItemStack())&&!d.getCommandTags().contains("monvhua_physics_test"));
            require(corpses.size()==1&&!CorpsePoseRecord.present(corpses.getFirst().getItemStack()),"Missing core broke legacy death");
        });
        context.runOnClient(client->client.player.requestRespawn());context.waitTicks(10);
    }
    static void run(ClientGameTestContext context,TestSingleplayerContext world) {
        int[] id={0};
        world.getServer().runCommand("tp @a 0.5 80 5.5 180 10");context.waitTicks(5);
        context.runOnClient(client->client.getNetworkHandler().sendChatCommand("mvcorpse physics test"));
        context.waitTicks(3);
        world.getServer().runOnServer(server->{
            var player=server.getPlayerManager().getPlayerList().getFirst();
            var candidates=server.getOverworld().getEntitiesByClass(ItemDisplayEntity.class,player.getBoundingBox().expand(10),
                    d->d.getCommandTags().contains("monvhua_physics_test"));
            require(!candidates.isEmpty(),"Native scene did not start: "+CorpsePhysicsCoordinator.status());id[0]=candidates.getLast().getId();
            require(CorpsePhysicsCoordinator.pending(candidates.getLast()),"Live solve finished instantly");
            require(!CorpsePoseRecord.present(candidates.getLast().getItemStack()),"Live pose persisted as SETTLED");
        });
        world.getServer().runCommand("tick freeze");context.waitTicks(3);
        int[] frozen={-1};
        context.runOnClient(client->{var v=CorpsePhysicsCoordinator.liveViews().getFirst();frozen[0]=v.session().frames().getLast().step();});
        context.waitTicks(8);
        context.runOnClient(client->{var v=CorpsePhysicsCoordinator.liveViews().getFirst();require(v.session().frames().getLast().step()==frozen[0],"Frozen game advanced native physics");});
        context.takeScreenshot("physics-live-frozen-start");
        world.getServer().runCommand("tick unfreeze");
        context.waitFor(client->client.world.getEntityById(id[0]) instanceof ItemDisplayEntity d && CorpsePoseRecord.read(CorpsePoseRecord.data(d.getItemStack())).map(p->p.source().equals("generated")).orElse(false),200);
        context.runOnClient(client->{
            var display=(ItemDisplayEntity)client.world.getEntityById(id[0]);
            var record=CorpsePoseRecord.read(CorpsePoseRecord.data(display.getItemStack())).orElseThrow();
            require(record.definition().equals("slim_six_v1"),"Wrong slim definition");
            var renderer=new com.kuilunfuzhe.monvhua.renderer.body.special.CombinedBodySpecialModelRenderer(client.getLoadedEntityModels());
            var data=renderer.getData(display.getItemStack());
            var document=new PhysicsPoseDocument("SETTLED",0,record.parts(),0,"actual solve",record.definition());
            PhysicsPreviewChecks.check(document,renderer.physicsPreviewSurfaces(StandardBodyPhysicsAdapter.matrices(document,true),data),true);
        });
        // A callback from a cancelled request cannot apply later; topology changes invalidate results.
        world.getServer().runOnServer(server->{
            var display=(ItemDisplayEntity)server.getOverworld().getEntityById(id[0]);
            var before=display.getItemStack().copy();
            require(CorpsePhysicsCoordinator.submit(display,display.getPos(),0,true,.3f),"Cancel setup");
            require(CorpseCarryService.handle(server.getPlayerManager().getPlayerList().getFirst(),display.getId(),false).contains("生成"),"Carry not gated");
            CorpsePhysicsCoordinator.cancel(display,"test cancel");
            require(net.minecraft.item.ItemStack.areEqual(before,display.getItemStack()),"Cancel changed item");
        });context.waitTicks(4);
        world.getServer().runOnServer(server->{
            var display=(ItemDisplayEntity)server.getOverworld().getEntityById(id[0]);
            require(CorpsePhysicsCoordinator.submit(display,display.getPos(),0,true,.3f),"Terrain-change setup");
            server.getOverworld().setBlockState(BlockPos.ofFloored(display.getPos().add(2,1,0)),net.minecraft.block.Blocks.STONE.getDefaultState());
        });context.waitTicks(15);
        world.getServer().runOnServer(server->{
            require(CorpsePhysicsCoordinator.status().contains("地形"),"Terrain change not invalidated: "+CorpsePhysicsCoordinator.status());
            var display=(ItemDisplayEntity)server.getOverworld().getEntityById(id[0]);
            require(CorpsePoseRecord.read(CorpsePoseRecord.data(display.getItemStack())).orElseThrow().revision()==1,"Stale result committed");
            server.getOverworld().setBlockState(BlockPos.ofFloored(display.getPos().add(2,1,0)),net.minecraft.block.Blocks.AIR.getDefaultState());
        });
        context.runOnClient(client->client.getNetworkHandler().sendChatCommand("mvcorpse physics auto on"));context.waitTicks(3);
        world.getServer().runCommand("tag @a add dead_body");
        world.getServer().runOnServer(server->{
            var player=server.getPlayerManager().getPlayerList().getFirst();
            var feet=player.getPos();
            player.setOnGround(false);
            require(CorpsePhysicsCoordinator.hasNearbySupport(player),"Ground flag false rejected real floor support");
            player.setPosition(feet.add(0,1,0));
            require(!CorpsePhysicsCoordinator.hasNearbySupport(player),"Airborne body incorrectly considered supported");
            player.setPosition(feet);player.setOnGround(false);
            // Kill in this same server task so a movement tick cannot repair onGround first.
            try {server.getCommandManager().getDispatcher().execute("kill @a",server.getCommandSource());}
            catch(com.mojang.brigadier.exceptions.CommandSyntaxException e){throw new AssertionError(e);}
        });
        context.waitTicks(10);
        world.getServer().runOnServer(server->{
            var live=CorpsePhysicsCoordinator.liveViews();require(live.size()==1,"Death live view missing");
            var view=live.getFirst();var frames=view.session().frames();
            require(!frames.isEmpty()&&frames.getLast().step()>0&&frames.getLast().step()<150,"Death not advancing in real time");
            var corpse=(ItemDisplayEntity)server.getOverworld().getEntity(view.display());id[0]=corpse.getId();
            require(CorpsePhysicsCoordinator.pending(corpse)&&!CorpsePoseRecord.present(corpse.getItemStack()),"Death finalized before falling");
        });
        // Dead local players may not receive newly tracked entity data until respawn.
        // The live view must hold its terminal frame throughout that interval.
        context.waitFor(client->CorpsePhysicsCoordinator.liveViews().stream().anyMatch(v->v.committedRevision()>0),240);
        world.getServer().runOnServer(server->{
            var corpses=server.getOverworld().getEntitiesByClass(ItemDisplayEntity.class,new Box(-8,78,-8,8,86,8),
                    d->Forensics.hasBinding(d.getItemStack())&&!d.getCommandTags().contains("monvhua_physics_test"));
            require(corpses.size()==1,"Death did not produce exactly one corpse: "+corpses.size());
            var corpse=corpses.getFirst();
            var pose=CorpsePoseRecord.read(CorpsePoseRecord.data(corpse.getItemStack()));
            require(pose.isPresent()&&pose.get().source().equals("generated"),"Real death did not apply: "+CorpsePhysicsCoordinator.status());
            var view=CorpsePhysicsCoordinator.liveViews().stream().filter(v->v.display().equals(corpse.getUuid())).findFirst().orElseThrow();
            require(pose.get().parts().equals(view.session().frames().getLast().poses()),"Stored pose differs from terminal live frame");
            require(Forensics.valid(server.getOverworld(),corpse.getItemStack(),corpse.getUuid().toString()),"Death binding lost");
            require(CorpsePoseGeometry.supported(server.getOverworld(),corpse,CorpsePoseRecord.data(corpse.getItemStack()),corpse.getPos()),"Death pose unsupported");
            id[0]=corpse.getId();
        });
        context.runOnClient(client->client.player.requestRespawn());context.waitTicks(15);
        context.waitFor(client->client.world.getEntityById(id[0]) instanceof ItemDisplayEntity d&&CorpsePoseRecord.present(d.getItemStack()),200);
        context.waitFor(client->CorpsePhysicsCoordinator.liveViews().isEmpty(),40);
        context.runOnClient(client->{require(!LiveCorpseRenderer.hides(client.player),"Respawned player hidden");require(!LiveCorpseRenderer.hides(client.world.getEntityById(id[0])),"Static handoff still hides corpse");});
        world.getServer().runCommand("tp @a 0.5 82 9.5 180 40");context.waitTicks(5);
        context.runOnClient(client->client.options.hudHidden=true);
        try{context.takeScreenshot("physics-automatic-death");}finally{context.runOnClient(client->client.options.hudHidden=false);}
        context.runOnClient(client->client.getNetworkHandler().sendChatCommand("mvcorpse physics auto off"));context.waitTicks(3);
        lifecycle(context,world,id[0]);
        try{java.nio.file.Files.writeString(PhysicsPreviewClient.instance.directory().getParent().resolve("physics-automatic-test.txt"),
                "PASS: live native slim scene, game-clock freeze, baked model mapping, cancellation, terrain invalidation, real player death during simulation, exact terminal handoff, binding, support, respawn visibility.\n");}
        catch(java.io.IOException e){throw new AssertionError(e);}
    }
    private static void lifecycle(ClientGameTestContext context,TestSingleplayerContext world,int entityId) {
        var provider=new ControlledProvider();
        net.minecraft.item.ItemStack[] before={null};PhysicsPoseDocument[] result={null};
        world.getServer().runOnServer(server->{
            var display=(ItemDisplayEntity)server.getOverworld().getEntityById(entityId);
            before[0]=display.getItemStack().copy();var pose=CorpsePoseRecord.read(CorpsePoseRecord.data(before[0])).orElseThrow();
            result[0]=new PhysicsPoseDocument("SETTLED",100,pose.parts(),80,"delayed test",pose.definition());
            CorpsePhysicsCoordinator.attach(server,provider);
            require(CorpsePhysicsCoordinator.submit(display,display.getPos(),0,true,.3f),"Lifecycle setup");
            CorpsePhysicsCoordinator.cancel(display,"explicit cancellation");
            provider.futures.getFirst().complete(result[0]);
        });context.waitTicks(3);
        world.getServer().runOnServer(server->{
            var display=(ItemDisplayEntity)server.getOverworld().getEntityById(entityId);
            require(net.minecraft.item.ItemStack.areEqual(before[0],display.getItemStack()),"Late cancelled result mutated corpse");
            require(CorpsePhysicsCoordinator.submit(display,display.getPos(),0,true,.3f),"Epoch setup");
            CorpsePhysicsCoordinator.detach();provider.futures.getLast().complete(result[0]);
        });context.waitTicks(3);
        world.getServer().runOnServer(server->{
            var display=(ItemDisplayEntity)server.getOverworld().getEntityById(entityId);
            require(net.minecraft.item.ItemStack.areEqual(before[0],display.getItemStack()),"Result crossed world epoch");
            CorpsePhysicsCoordinator.attach(server,new LocalCorpsePhysicsProvider());
        });
    }
    private static final class ControlledProvider implements CorpsePhysicsBridge {
        final List<java.util.concurrent.CompletableFuture<PhysicsPoseDocument>> futures=new ArrayList<>();
        public int apiVersion(){return API_VERSION;}
        public String status(){return "controlled lifecycle test";}
        public java.util.concurrent.CompletableFuture<PhysicsPoseDocument> solve(Request request) {
            // Deliberately ignore cancellation, as a delayed foreign backend might do.
            var f=new java.util.concurrent.CompletableFuture<PhysicsPoseDocument>(){public boolean cancel(boolean interrupt){return false;}};
            futures.add(f);return f;
        }
        public void close() {}
    }
}
