package com.kuilunfuzhe.monvhua.features.physics_preview;

import com.kuilunfuzhe.monvhua.features.forensics.*;
import com.kuilunfuzhe.monvhua.features.forensics.physics.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.entity.*;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.util.math.*;
import static com.kuilunfuzhe.monvhua.features.physics_preview.PhysicsPreviewChecks.require;

/** Actual server damage/death calls, including totem rescue and projectile direction provenance. */
final class DirectionalCorpseGameChecks {
    static void run(ClientGameTestContext context,TestSingleplayerContext world) {
        world.getServer().runCommand("mvcorpse physics auto on");
        world.getServer().runCommand("gamemode survival @a");
        world.getServer().runCommand("tag @a add dead_body");
        // Exercise the public command and capture distinct live frames from an observer position.
        world.getServer().runCommand("tp @a 0.5 80 5.5 180 10");context.waitTicks(4);
        context.runOnClient(client->client.getNetworkHandler().sendChatCommand("mvcorpse physics test-motion front"));context.waitTicks(5);
        java.util.UUID[] debug={null};
        context.runOnClient(client->{debug[0]=CorpsePhysicsCoordinator.liveViews().getFirst().display();client.options.hudHidden=true;});
        try {
            context.takeScreenshot("physics-f5-front-start");context.waitTicks(10);context.takeScreenshot("physics-f5-front-falling");
            context.waitFor(client->CorpsePhysicsCoordinator.liveViews().isEmpty(),240);context.takeScreenshot("physics-f5-front-settled");
        }finally{context.runOnClient(client->client.options.hudHidden=false);}
        world.getServer().runOnServer(server->{var d=server.getOverworld().getEntity(debug[0]);if(d!=null)d.discard();});
        // A rescued hit must not carry its direction into a subsequent unrelated death.
        world.getServer().runOnServer(server->{
            var p=server.getPlayerManager().getPlayerList().getFirst();p.setPosition(.5,80,.5);p.setHealth(1);p.timeUntilRegen=0;
            p.setStackInHand(net.minecraft.util.Hand.OFF_HAND,new net.minecraft.item.ItemStack(net.minecraft.item.Items.TOTEM_OF_UNDYING));
            var mob=EntityType.ZOMBIE.create(server.getOverworld(),SpawnReason.TRIGGERED);mob.setPosition(.5,80,2.5);
            p.damage(server.getOverworld(),p.getDamageSources().mobAttack(mob),20);
            require(p.isAlive(),"Totem did not rescue player");require(DeathMotionContext.activeDepth()==0,"Damage context leaked after totem");
            require(CorpsePhysicsCoordinator.liveViews().isEmpty(),"Totem created corpse");p.clearStatusEffects();
        });
        for(String side:new String[]{"unknown","front","back","left","right","projectile","trident","vertical","fire"}) {
            world.getServer().runCommand("tp @a 0.5 80 0.5 0 0");context.waitTicks(4);
            String[] corpseId={null};java.util.UUID[] displayId={null};
            world.getServer().runOnServer(server->{
                var p=server.getPlayerManager().getPlayerList().getFirst();p.bodyYaw=0;p.setYaw(70); // Head/aim differs from body.
                p.setVelocity(Vec3d.ZERO);p.setHealth(1);p.timeUntilRegen=0;p.clearStatusEffects();
                if(side.equals("unknown"))p.kill(server.getOverworld());
                else if(side.equals("fire")) {
                    var prior=EntityType.ZOMBIE.create(server.getOverworld(),SpawnReason.TRIGGERED);prior.setPosition(.5,80,2.5);
                    p.setHealth(10);p.damage(server.getOverworld(),p.getDamageSources().mobAttack(prior),.1f);
                    p.setHealth(1);p.timeUntilRegen=0;p.setVelocity(Vec3d.ZERO);
                    p.damage(server.getOverworld(),p.getDamageSources().onFire(),10);
                }else {
                    var mob=EntityType.ZOMBIE.create(server.getOverworld(),SpawnReason.TRIGGERED);
                    var direction=CorpseInitialMotionResolver.test(0,java.util.Set.of("projectile","trident","vertical").contains(side)?"right":side);
                    mob.setPosition(p.getPos().add(-direction.dx()*2,0,-direction.dz()*2));
                    if(side.equals("trident")) {
                        var trident=EntityType.TRIDENT.create(server.getOverworld(),SpawnReason.TRIGGERED);trident.setVelocity(1,0,0);trident.setOwner(mob);
                        p.damage(server.getOverworld(),p.getDamageSources().trident(trident,mob),10);
                    }else if(side.equals("projectile")||side.equals("vertical")) {
                        var arrow=EntityType.ARROW.create(server.getOverworld(),SpawnReason.TRIGGERED);
                        arrow.setPosition(p.getPos());arrow.setVelocity(side.equals("vertical")?new Vec3d(0,-1,0):new Vec3d(1,0,0));arrow.setOwner(mob);
                        mob.setPosition(.5,80,-5); // Shooter position disagrees with incoming projectile.
                        p.damage(server.getOverworld(),p.getDamageSources().arrow(arrow,mob),10);
                    }else {
                        // Same-tick previous nonlethal attack comes from the opposite side.
                        var prior=EntityType.ZOMBIE.create(server.getOverworld(),SpawnReason.TRIGGERED);
                        prior.setPosition(p.getPos().add(direction.dx()*2,0,direction.dz()*2));
                        p.setHealth(10);p.damage(server.getOverworld(),p.getDamageSources().mobAttack(prior),.1f);
                        p.setHealth(1);p.timeUntilRegen=0;p.setVelocity(Vec3d.ZERO);
                        p.damage(server.getOverworld(),p.getDamageSources().mobAttack(mob),10);
                    }
                }
                require(!p.isAlive(),"Damage did not kill "+side);require(DeathMotionContext.activeDepth()==0,"Damage scope leaked");
                corpseId[0]=ForensicStore.get(server).life(p.getUuid()).getString("corpse","");
                var v=CorpsePhysicsCoordinator.liveViews().stream().filter(view->p.getUuid().equals(view.victim())).findFirst().orElseThrow();displayId[0]=v.display();
            });
            context.waitFor(client->CorpsePhysicsCoordinator.liveViews().stream().anyMatch(v->v.display().equals(displayId[0])&&v.committedRevision()>0),260);
            world.getServer().runOnServer(server->{
                var d=ForensicStore.get(server).corpse(corpseId[0]).getCompoundOrEmpty("pose_generation_diagnostics");
                require(d.getInt("algorithm_version",0)==2,"Missing motion diagnostics: "+side);
                require(d.getFloat("yaw",999)==0,"Used head yaw instead of body yaw");
                require(d.getString("incoming","").equals(java.util.Set.of("projectile","trident").contains(side)?"right":java.util.Set.of("vertical","fire").contains(side)?"unknown":side),"Wrong incoming direction: "+side+" "+d);
                if(side.equals("projectile")||side.equals("trident"))require(d.getString("motion_source","").equals("projectile")&&d.getFloat("vx",0)>0,"Shooter location overrode projectile velocity");
                var display=(ItemDisplayEntity)server.getOverworld().getEntity(displayId[0]);
                require(!CorpsePoseRecord.data(display.getItemStack()).contains("pose_generation_diagnostics"),"Private motion leaked into item");
            });
            context.takeScreenshot("physics-direction-"+side);
            context.runOnClient(client->client.player.requestRespawn());context.waitTicks(15);
            world.getServer().runCommand("tp @a 0.5 80 5.5 180 20");context.waitTicks(5);
            context.waitFor(client->CorpsePhysicsCoordinator.liveViews().isEmpty(),100);
            world.getServer().runOnServer(server->{var display=server.getOverworld().getEntity(displayId[0]);if(display!=null)display.discard();});
        }
        world.getServer().runCommand("mvcorpse physics auto off");
        try{java.nio.file.Files.writeString(PhysicsPreviewClient.instance.directory().getParent().resolve("physics-direction-test.txt"),"PASS: command animation, totem cleanup, same-tick distinct hits, unknown/front/back/left/right death, fire does not inherit attacker, body versus head yaw, arrow/trident versus shooter direction, vertical projectile fallback, private diagnostics, terminal handoff.\n");}
        catch(java.io.IOException e){throw new AssertionError(e);}
    }
}
