package com.kuilunfuzhe.monvhua.features.physics_preview;

import com.kuilunfuzhe.monvhua.features.forensics.*;
import com.kuilunfuzhe.monvhua.features.forensics.physics.*;
import com.kuilunfuzhe.monvhua.features.block.body.BodyPartManager;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.entity.*;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.entity.projectile.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.*;
import net.minecraft.nbt.*;
import java.util.*;
import java.nio.file.*;
import static com.kuilunfuzhe.monvhua.features.physics_preview.PhysicsPreviewChecks.require;

/** Real attack()/projectile tick collision, not handcrafted victim DamageSource calls. */
final class CorpseRealEventGameChecks {
    private record Saved(UUID display,String corpse,UUID observer,NbtCompound visual,NbtCompound observation,NbtCompound initial,net.minecraft.component.type.ProfileComponent profile){}
    private static final List<Saved> saved=new ArrayList<>();
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        saved.clear();
        context.runOnClient(client->{
            var renderer=new com.kuilunfuzhe.monvhua.renderer.body.special.CombinedBodySpecialModelRenderer(client.getLoadedEntityModels());
            var item=new net.minecraft.item.ItemStack(net.minecraft.item.Items.NETHERITE_SCRAP);var marks=PhysicsPreviewFixtures.marks();
            item.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,net.minecraft.component.type.NbtComponent.of(marks));
            require(renderer.getData(item)==null,"Fallback fixture unexpectedly resolves skin");
            for(boolean slim:new boolean[]{false,true}){var data=LiveCorpseRenderer.renderData(renderer,item,slim);require(data.armModel().equals(slim?"slim":"default")&&data.customData().equals(marks),"Missing skin fallback lost shape/marks");}
        });
        world.getServer().runCommand("mvcorpse physics auto on");world.getServer().runCommand("tag @a add dead_body");
        world.getServer().runCommand("gamemode survival @a");
        var report=new ArrayList<String>();
        for(String cause:List.of("player_attack","sprint_attack","arrow_flight","trident_flight")){
            world.getServer().runCommand("tp @a 0.5 80 0.5 0 0");context.waitTicks(5);
            world.getServer().runOnServer(server->{
                var w=server.getOverworld();var victim=server.getPlayerManager().getPlayerList().getFirst();
                victim.setHealth(.01f);victim.timeUntilRegen=0;victim.setVelocity(Vec3d.ZERO);victim.bodyYaw=0;victim.setYaw(0);victim.clearStatusEffects();
                victim.setStackInHand(net.minecraft.util.Hand.OFF_HAND,net.minecraft.item.ItemStack.EMPTY);
                if(cause.endsWith("attack")){
                    // Isolated server test actor: normal PlayerEntity.attack path, no extra client/network session.
                    var attacker=new ServerPlayerEntity(server,w,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"PhysicsTestActor"),net.minecraft.network.packet.c2s.common.SyncedClientOptions.createDefault()){
                        @Override public void increaseStat(net.minecraft.stat.Stat<?> stat,int amount){} // test actor has no network connection
                    };
                    attacker.setPosition(.5,80,2.0);attacker.setYaw(180);attacker.setSprinting(cause.equals("sprint_attack"));
                    attacker.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND_SWORD));
                    attacker.attack(victim);
                    require(!victim.isAlive(),"Real player attack did not kill: "+cause);
                }else{
                    var shooter=EntityType.ZOMBIE.create(w,SpawnReason.TRIGGERED);shooter.setPosition(-2,80,.5);
                    PersistentProjectileEntity projectile=cause.equals("arrow_flight")?EntityType.ARROW.create(w,SpawnReason.TRIGGERED):EntityType.TRIDENT.create(w,SpawnReason.TRIGGERED);
                    projectile.setOwner(shooter);projectile.setPosition(-1.5,81.1,.5);projectile.setVelocity(1,0,0);projectile.setNoGravity(true);
                    require(w.spawnEntity(projectile),"Projectile spawn failed");shooter.setPosition(.5,80,4.5);
                    require(victim.isAlive(),"Projectile test bypassed flight");
                }
            });
            context.waitFor(c->!c.player.isAlive(),60);
            UUID[] displayId={null};String[] corpseId={null};
            world.getServer().runOnServer(server->{
                var p=server.getPlayerManager().getPlayerList().getFirst();corpseId[0]=ForensicStore.get(server).life(p.getUuid()).getString("corpse","");
                var view=CorpsePhysicsCoordinator.liveViews().stream().filter(v->p.getUuid().equals(v.victim())).findFirst().orElseThrow();displayId[0]=view.display();
                require(DeathMotionContext.activeDepth()==0,"Damage scope leaked");
            });
            context.waitFor(c->CorpsePhysicsCoordinator.liveViews().stream().anyMatch(v->v.display().equals(displayId[0])&&v.committedRevision()>0),260);
            world.getServer().runOnServer(server->{
                var corpse=ForensicStore.get(server).corpse(corpseId[0]);var d=corpse.getCompoundOrEmpty("pose_generation_diagnostics");
                String expected=cause.endsWith("flight")?"projectile":"melee";
                require(d.getString("motion_source","").equals(expected),"Wrong source for "+cause+": "+d);
                if(cause.endsWith("flight"))require(d.getFloat("vx",0)>0&&d.getString("incoming","").equals("right"),"Projectile owner position overrode flight direction");
                else require(d.getString("incoming","").equals("front"),"Actual melee direction incorrect");
                require(CorpsePhysicsDiagnostics.latest().stage()==CorpsePhysicsDiagnostics.Stage.APPLIED,"Missing successful diagnostic");
            });
            context.runOnClient(c->c.player.requestRespawn());context.waitTicks(15);
            world.getServer().runCommand("gamemode creative @a");world.getServer().runCommand("tp @a 0.5 80 4.5 180 30");context.waitTicks(5);
            context.waitFor(c->CorpsePhysicsCoordinator.liveViews().isEmpty(),100);
            world.getServer().runOnServer(server->{
                var w=server.getOverworld();var player=server.getPlayerManager().getPlayerList().getFirst();var display=(ItemDisplayEntity)w.getEntity(displayId[0]);
                require(display!=null,"Real corpse lost");player.setPosition(display.getPos().add(0,0,1));
                var painted=CorpsePoseRecord.data(display.getItemStack());painted.put("model_paint_faces",PhysicsPreviewFixtures.marks().getListOrEmpty("model_paint_faces").copy());CorpsePoseService.commitStatic(display,painted);
                var before=CorpsePoseRecord.data(display.getItemStack());require(!before.getListOrEmpty("model_paint_faces").isEmpty(),"Paint fixture absent");
                require(!before.contains("pose_generation_diagnostics"),"Private diagnostics leaked into corpse item");
                EvidenceSnapshots.observe(player,display,"torso");require(!ForensicStore.get(server).observations(player.getUuid()).getCompoundOrEmpty(corpseId[0]).isEmpty(),"Real corpse observation failed");
                String carry=CorpseCarryService.handle(player,display.getId(),false);require(CorpseCarryService.isCarried(display),"Real corpse carry failed: "+carry);
                CorpseCarryService.handle(player,display.getId(),true);require(!CorpseCarryService.isCarried(display),"Real corpse release failed");
                var after=CorpsePoseRecord.data(display.getItemStack());
                for(String key:List.of("corpse_pose","corpse_wounds","model_paint_faces","arm_model","local_skin"))require(Objects.equals(before.get(key),after.get(key)),"Carry modified "+key);
                // Exercise actual entity WriteView -> compressed disk NBT -> ReadView, including display transform.
                try{
                    var writer=net.minecraft.storage.NbtWriteView.create(net.minecraft.util.ErrorReporter.EMPTY,w.getRegistryManager());display.writeData(writer);
                    var path=PhysicsPreviewClient.instance.directory().getParent().resolve("g4-corpse-"+cause+".nbt");NbtIo.writeCompressed(writer.getNbt(),path);
                    var saved=NbtIo.readCompressed(path,NbtSizeTracker.of(1024*1024));
                    var restored=EntityType.ITEM_DISPLAY.create(w,SpawnReason.LOAD);restored.readData(net.minecraft.storage.NbtReadView.create(net.minecraft.util.ErrorReporter.EMPTY,w.getRegistryManager(),saved));
                    require(net.minecraft.item.ItemStack.areEqual(display.getItemStack(),restored.getItemStack()),"Disk entity roundtrip changed item");
                    require(display.getPos().equals(restored.getPos()),"Disk entity roundtrip changed origin");
                    require(CorpsePoseGeometry.surfaces(display,after).stream().map(CorpsePoseGeometry::bounds).toList().equals(CorpsePoseGeometry.surfaces(restored,CorpsePoseRecord.data(restored.getItemStack())).stream().map(CorpsePoseGeometry::bounds).toList()),"Disk entity geometry changed");
                }catch(java.io.IOException e){throw new AssertionError(e);}
                report.add("PASS "+cause+": actual attack/collision, direction, observe/carry/drop, compressed entity disk roundtrip");
            });
            context.runOnClient(client->{
                var samples=LiveCorpseRenderer.diagnosticSamples(displayId[0]);require(!samples.isEmpty(),"No real rendering samples");
                double previous=-1;var csv=new StringBuilder("nanos,displayed,latest,mode,waits,resyncs\n");
                for(var sample:samples){
                    require(sample.displayed()<=sample.latest()&&sample.displayed()>=previous,"Playback reversed or extrapolated");previous=sample.displayed();
                    csv.append(sample.nanos()).append(',').append(sample.displayed()).append(',').append(sample.latest()).append(',').append(sample.mode()).append(',').append(sample.waits()).append(',').append(sample.resyncs()).append('\n');
                }
                try{Files.writeString(PhysicsPreviewClient.instance.directory().getParent().resolve("g4-playback-"+cause+".csv"),csv);}catch(java.io.IOException e){throw new AssertionError(e);}
            });
            diagnosticExport(context,world,displayId[0]);
            context.takeScreenshot("physics-g4-"+cause);
            world.getServer().runOnServer(server->{
                var d=(ItemDisplayEntity)server.getOverworld().getEntity(displayId[0]);var observer=server.getPlayerManager().getPlayerList().getFirst().getUuid();var store=ForensicStore.get(server);
                saved.add(new Saved(displayId[0],corpseId[0],observer,CorpsePoseRecord.data(d.getItemStack()).copy(),store.observations(observer).getCompoundOrEmpty(corpseId[0]).copy(),store.corpse(corpseId[0]).getCompoundOrEmpty("initial_generated_pose").copy(),d.getItemStack().get(net.minecraft.component.DataComponentTypes.PROFILE)));
            });
            world.getServer().runCommand("gamemode survival @a");
        }
        world.getServer().runCommand("mvcorpse physics auto off");
        try{Files.writeString(PhysicsPreviewClient.instance.directory().getParent().resolve("physics-g4-real-events.txt"),String.join("\n",report)+"\n");}catch(java.io.IOException e){throw new AssertionError(e);}
    }
    static void verifyReload(ClientGameTestContext context,TestSingleplayerContext world){
        context.waitTicks(25);
        world.getServer().runOnServer(server->{
            require(CorpsePhysicsCoordinator.liveViews().isEmpty(),"Reload restarted static physics");
            for(var expected:saved){
                var display=(ItemDisplayEntity)server.getOverworld().getEntity(expected.display());require(display!=null,"Saved corpse not reloaded");
                var data=CorpsePoseRecord.data(display.getItemStack());
                require(Objects.equals(expected.profile(),display.getItemStack().get(net.minecraft.component.DataComponentTypes.PROFILE)),"Reload modified skin profile");
                for(String key:List.of("corpse_pose","corpse_wounds","model_paint_faces","arm_model","local_skin"))require(Objects.equals(expected.visual().get(key),data.get(key)),"World reload modified "+key);
                var linked=BodyPartManager.DISPLAY_TO_INTERACTION.get(display.getUuid());require(linked!=null&&server.getOverworld().getEntity(linked)!=null,"Reload lost interaction binding");
                require(Forensics.valid(server.getOverworld(),display.getItemStack(),display.getUuid().toString()),"Reload lost forensic ownership");
                var store=ForensicStore.get(server);require(expected.observation().equals(store.observations(expected.observer()).getCompoundOrEmpty(expected.corpse())),"Reload changed observation");
                require(expected.initial().equals(store.corpse(expected.corpse()).getCompoundOrEmpty("initial_generated_pose")),"Reload changed initial case pose");
            }
        });
        try{Files.writeString(PhysicsPreviewClient.instance.directory().getParent().resolve("physics-g4-real-events.txt"),"PASS world close/reopen: 4 real corpses, pose/wounds/paint/skin metadata, interactions, binding, observation, initial pose; no restarted sessions\n",StandardOpenOption.APPEND);}catch(java.io.IOException e){throw new AssertionError(e);}
        saved.clear();
    }
    private static void diagnosticExport(ClientGameTestContext context,TestSingleplayerContext world,UUID corpse){
        Path directory=net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("simplephysics-diagnostics");
        var before=files(directory);world.getServer().runCommand("mvcorpse physics export-last reproduction");
        context.waitFor(c->files(directory).size()>before.size(),100);
        var added=files(directory);added.removeAll(before);String text;
        try{text=Files.readString(added.iterator().next());}catch(java.io.IOException e){throw new AssertionError(e);}
        require(!text.contains(corpse.toString())&&!text.contains("attacker")&&!text.contains("textures/"),"Identity leaked in export");
        final String json=text;
        // Optional core is accessed only inside the provider-backed G4 test, not normal common code.
        var doc=com.klfz.simplephysics.api.RagdollDiagnosticDocument.decode(json);require(doc.input().terrain()!=null&&doc.hostStage().equals("APPLIED"),"Bad diagnostic export");
        try(var backend=new com.klfz.simplephysics.backend.PhysicsService()){
            var replay=backend.solve(doc.input().request()).get(20,java.util.concurrent.TimeUnit.SECONDS);
            require(replay.finalPose().equals(doc.result().finalPose()),"Export did not reproduce exact terminal");
        }catch(Exception e){throw new AssertionError(e);}
    }
    private static Set<Path> files(Path dir){try{if(!Files.exists(dir))return new HashSet<>();try(var s=Files.list(dir)){return s.filter(p->p.toString().endsWith(".json")).collect(java.util.stream.Collectors.toCollection(HashSet::new));}}catch(java.io.IOException e){throw new AssertionError(e);}}
}
