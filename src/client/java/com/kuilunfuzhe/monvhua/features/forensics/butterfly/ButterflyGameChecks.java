package com.kuilunfuzhe.monvhua.features.forensics.butterfly;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.*;
import com.kuilunfuzhe.monvhua.network.forensics.ButterflyPackets;
import java.util.*;
import java.nio.file.*;

/** Isolated real-client optional particle ABI and motion smoke check. */
public final class ButterflyGameChecks {
    private ButterflyGameChecks(){}
    public static void run(ClientGameTestContext context){
        try(var world=context.worldBuilder().create()){
            world.getServer().runOnServer(server->{
                for(BlockPos p:BlockPos.iterate(-4,78,-4,4,78,4))server.getOverworld().setBlockState(p,Blocks.STONE.getDefaultState());
            });
            world.getServer().runCommand("tp @a 0.5 80 4.5 180 0");
            context.waitTicks(30);world.getClientWorld().waitForChunksRender();
            world.getServer().runOnServer(server->{
                var w=server.getOverworld();
                w.setBlockState(new BlockPos(2,80,0),Blocks.STONE.getDefaultState());
                if(ButterflyClearance.clear(w,new Vec3d(1.5,80.5,.5),new Vec3d(2.5,80.5,.5)))throw new AssertionError("Solid wall sweep passed");
                w.setBlockState(new BlockPos(2,79,1),Blocks.OAK_FENCE.getDefaultState());
                if(ButterflyClearance.clear(w,new Vec3d(2.5,80.2,1.5),new Vec3d(2.5,80.2,1.5)))throw new AssertionError("Fence extended collision missed");
                w.setBlockState(new BlockPos(2,80,2),Blocks.STONE_SLAB.getDefaultState());
                if(ButterflyClearance.clear(w,new Vec3d(2.5,80.2,2.5),new Vec3d(2.5,80.2,2.5)))throw new AssertionError("Slab collision missed");
                if(!ButterflyClearance.clear(w,new Vec3d(2.5,80.8,2.5),new Vec3d(2.5,80.8,2.5)))throw new AssertionError("Slab upper clearance blocked");
                Vec3d unknown=new Vec3d(100000,80,100000);
                if(w.isChunkLoaded(BlockPos.ofFloored(unknown)))throw new AssertionError("Expected unloaded region");
                if(ButterflyClearance.clear(w,unknown,unknown)||w.isChunkLoaded(BlockPos.ofFloored(unknown)))throw new AssertionError("Unknown chunk queried/loaded");
            });
            for(int i=0;i<4;i++){
                int tick=i*4;
                context.runOnClient(client->{
                    Vec3d anchor=new Vec3d(.5,80.5,.5);
                    if(!ButterflyClearance.clear(client.world,anchor,anchor))throw new AssertionError("Smoke source blocked");
                    var flight=new ButterflyPackets.Flight(1,123,160,Collections.nCopies(5,anchor));
                    BloodButterflyClient.accept(new ButterflyPackets.Sync(client.world.getRegistryKey().getValue().toString(),tick,.12,List.of(flight)));
                });
                context.waitTicks(4);
            }
            context.runOnClient(client->{if(!BloodButterflyClient.controlledVisualWorks())throw new AssertionError("External butterfly hook/drift did not work");});
            lifecycle(context,world);
        }
        try{Files.writeString(Path.of("butterfly-smoke-pass.txt"),"PASS: real optional particle/hook/bounded random drift; actual wall/fence/slab sweeps; unloaded chunk rejection; server flight advanced without blocked position; large corpse/limb pools only; individual player locks, continuous curved motion, orbit, non-stacking 10% attraction, random 100..416 tick lifetime, expiry residue, Carpet counts and spectator exclusion.\n");}
        catch(java.io.IOException e){throw new AssertionError(e);}
    }
    private static void lifecycle(ClientGameTestContext context,
            net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext world){
        String[] corpse={null};int[] phase={0};
        var initial=new java.util.HashMap<Integer,BloodButterflyService.Observation>();
        var latest=new java.util.HashMap<Integer,BloodButterflyService.Observation>();
        var pauses=new java.util.HashMap<Integer,Integer>();
        var orbitStart=new java.util.HashMap<Integer,Vec3d>();
        boolean[] saw={false,false};
        world.getServer().runOnServer(server->{
            var w=server.getOverworld();
            for(BlockPos p:BlockPos.iterate(-12,78,-12,12,78,12))w.setBlockState(p,Blocks.STONE.getDefaultState());
            var player=server.getPlayerManager().getPlayerList().getFirst();
            player.changeGameMode(net.minecraft.world.GameMode.CREATIVE);
            player.getAbilities().flying=true;player.sendAbilitiesUpdate();
            server.getCommandManager().executeWithPrefix(server.getCommandSource(),"tp @a 0.5 80 4.5");
            player.addCommandTag("dead_body");
            com.kuilunfuzhe.monvhua.features.block.body.BodyPartManager.spawnDeath(player,player.getDamageSources().genericKill());
            for(var display:com.kuilunfuzhe.monvhua.features.forensics.CorpseLoadedIndex.snapshot()){
                String id=com.kuilunfuzhe.monvhua.features.forensics.Forensics.binding(display.getItemStack()).getString("id","");
                if(!id.isEmpty()){corpse[0]=id;break;}
            }
            if(corpse[0]==null)throw new AssertionError("Missing real corpse record fixture");
            patch(w,corpse[0],"drop",0,.75f,new BlockPos(0,78,0));
            patch(w,corpse[0],"small",1,.2f,new BlockPos(-3,78,0));
            phase[0]=server.getTicks()%200;
        });
        context.waitTicks(200-phase[0]+5);
        world.getServer().runOnServer(server->{
            if(!BloodButterflyService.positions(server.getOverworld()).isEmpty())throw new AssertionError("Corpse alone, drops or small pools must not emit");
            var w=server.getOverworld();
            var nearest=ButterflyResidue.nearest(w,new Vec3d(1.8,80.5,.5));
            if(nearest==null||nearest.getSide()!=Direction.WEST||!nearest.getBlockPos().equals(new BlockPos(2,80,0)))throw new AssertionError("Closer wall must win over ground");
            patch(w,corpse[0],"near",1,.75f,new BlockPos(0,78,4));
            // Another tile of the same pool event must not create another cohort.
            patch(w,corpse[0],"near",1,.75f,new BlockPos(1,78,4));
            patch(w,corpse[0],"far",1,.75f,new BlockPos(10,78,0));
            // Obstruct the far source's direct route: navigation must move while searching.
            for(BlockPos p:BlockPos.iterate(4,79,-1,4,82,3))w.setBlockState(p,Blocks.STONE.getDefaultState());
            if(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("carpet")){
                var fake=bot(server);fake.refreshPositionAndAngles(6.5,80,4.5,0,0);
            }
            phase[0]=server.getTicks()%200;
        });
        context.waitTicks(200-phase[0]+5);
        world.getServer().runOnServer(server->{
            var w=server.getOverworld();var flights=BloodButterflyService.observations(w);
            int players=(int)server.getPlayerManager().getPlayerList().stream().filter(p->!p.isSpectator()).count();
            if(flights.size()!=players*2)throw new AssertionError("Two large pool sites must emit one per eligible player: "+flights);
            var counts=new java.util.HashMap<UUID,Integer>();
            for(var f:flights){
                if(f.lifetime()<100||f.lifetime()>416)throw new AssertionError("Lifetime outside random 5..20.8 second range");
                initial.put(f.id(),f);latest.put(f.id(),f);counts.merge(f.target(),1,Integer::sum);
            }
            if(counts.size()!=players||counts.values().stream().anyMatch(n->n!=2))throw new AssertionError("Flights did not receive distinct locked players");
            // Remove sources; existing flights must continue, and residues must never breed.
            var store=com.kuilunfuzhe.monvhua.features.forensics.blood.BloodTraceStore.get(w);
            for(var e:store.entries())store.remove(e.patch().id());
        });
        int maxLifetime=initial.values().stream().mapToInt(BloodButterflyService.Observation::lifetime).max().orElseThrow();
        for(int tick=0;tick<maxLifetime+4;tick++){
            context.waitTicks(1);
            world.getServer().runOnServer(server->{
                var w=server.getOverworld();
                for(var f:BloodButterflyService.observations(w)){
                    var first=initial.get(f.id());var old=latest.put(f.id(),f);
                    if(first==null||!first.target().equals(f.target()))throw new AssertionError("Flight switched to a different player");
                    if(!ButterflyClearance.clear(w,f.position(),f.position()))throw new AssertionError("Unsafe server position");
                    int pause=old.position().squaredDistanceTo(f.position())<1e-8?pauses.getOrDefault(f.id(),0)+1:0;
                    pauses.put(f.id(),pause);
                    if(pause>4)throw new AssertionError("Flight remained stationary instead of searching while moving");
                    if(f.boosted()){
                        saw[0]=true;
                        double distance=old.position().distanceTo(f.position());
                        if(Math.abs(distance-.065*1.1)>1e-5)throw new AssertionError("Attraction must be exactly 10 percent and not stack: "+distance);
                    }
                    if(f.arrived()){
                        orbitStart.putIfAbsent(f.id(),f.position());
                        if(orbitStart.get(f.id()).distanceTo(f.position())>.4)saw[1]=true;
                    }
                }
            });
        }
        world.getServer().runOnServer(server->{
            var w=server.getOverworld();
            if(!BloodButterflyService.positions(w).isEmpty())throw new AssertionError("Flights outlived their random lifetime");
            if(!saw[0]||!saw[1])throw new AssertionError("Missing actual arrival attraction or continued orbit motion");
            var traces=com.kuilunfuzhe.monvhua.features.forensics.blood.BloodTraceStore.get(w);
            if(traces.entries().stream().noneMatch(e->!ButterflyResidue.isBirthSource(e)&&e.source().isEmpty()&&e.patch().radius()<.08f))throw new AssertionError("No small anonymous expiry residue");
            phase[0]=server.getTicks()%200;
        });
        context.waitTicks(200-phase[0]+5);
        world.getServer().runOnServer(server->{
            var w=server.getOverworld();
            if(!BloodButterflyService.positions(w).isEmpty())throw new AssertionError("Residue bred new flights");
            for(var p:server.getPlayerManager().getPlayerList())if(p.getName().getString().equals("ButterflyBot"))p.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);
            patch(w,corpse[0],"spectator",1,.75f,new BlockPos(0,78,4));
            phase[0]=server.getTicks()%200;
        });
        context.waitTicks(200-phase[0]+5);
        world.getServer().runOnServer(server->{
            if(BloodButterflyService.observations(server.getOverworld()).size()!=1)throw new AssertionError("Spectator must not receive a flight");
            maximumLifeFixture();
            var store=com.kuilunfuzhe.monvhua.features.forensics.blood.BloodTraceStore.get(server.getOverworld());
            for(var entry:store.entries())store.remove(entry.patch().id());
        });
        context.waitTicks(30);
        world.getServer().runOnServer(server->{
            var flights=BloodButterflyService.observations(server.getOverworld());
            if(flights.size()!=1||!flights.getFirst().arrived())throw new AssertionError("Nearby assigned player must be reached");
            server.getCommandManager().executeWithPrefix(server.getCommandSource(),"tp @a[gamemode=!spectator] 8.5 80 0.5");
        });
        context.waitTicks(4);
        world.getServer().runOnServer(server->{
            var flights=BloodButterflyService.observations(server.getOverworld());
            if(flights.size()!=1||flights.getFirst().arrived())throw new AssertionError("Departing orbit target must resume locked path pursuit");
        });
        boolean[] returned={false};
        for(int tick=0;tick<320&&!returned[0];tick++){
            context.waitTicks(1);
            world.getServer().runOnServer(server->{
                var w=server.getOverworld();var flights=BloodButterflyService.observations(w);
                if(flights.size()!=1)throw new AssertionError("Maximum-life pursuit fixture disappeared before reaching moved target");
                var flight=flights.getFirst();
                if(!ButterflyClearance.clear(w,flight.position(),flight.position()))throw new AssertionError("Moved-target pursuit crossed a wall");
                returned[0]=flight.arrived();
            });
        }
        world.getServer().runOnServer(server->{
            if(!returned[0])throw new AssertionError("Pursuit failed to re-enter orbit after player moved behind the wall");
            server.getCommandManager().executeWithPrefix(server.getCommandSource(),"tp @a 200.5 80 4.5");
            phase[0]=server.getTicks()%200;
        });
        context.waitTicks(200-phase[0]+5);
        world.getServer().runOnServer(server->{if(!BloodButterflyService.positions(server.getOverworld()).isEmpty())throw new AssertionError("Outside 100 blocks must not produce or retain flights");});
    }
    /** Test-only fixture: choose the supported maximum lifetime so the detour has time to finish. */
    private static void maximumLifeFixture(){
        try{
            var flights=BloodButterflyService.class.getDeclaredField("FLIGHTS");flights.setAccessible(true);
            for(Object flight:(java.util.List<?>)flights.get(null)){
                var lifetime=flight.getClass().getDeclaredField("lifetime");lifetime.setAccessible(true);lifetime.setInt(flight,416);
            }
        }catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    private static void patch(net.minecraft.server.world.ServerWorld world,String corpse,String event,int kind,float radius,BlockPos pos){
        var patch=new com.kuilunfuzhe.monvhua.features.forensics.blood.BloodPatch(UUID.randomUUID().toString(),pos,Direction.UP,.5f,.5f,radius,kind,1,1);
        if(!com.kuilunfuzhe.monvhua.features.forensics.blood.BloodTraceService.submit(world,
                new com.kuilunfuzhe.monvhua.features.forensics.blood.BloodTraceStore.Entry(patch,corpse,event,world.getBlockState(pos).toString(),false)))throw new AssertionError("Pool fixture rejected");
    }

    private static net.minecraft.server.network.ServerPlayerEntity bot(net.minecraft.server.MinecraftServer server){
        try{
            var profile=new com.mojang.authlib.GameProfile(UUID.randomUUID(),"ButterflyBot");
            var options=net.minecraft.network.packet.c2s.common.SyncedClientOptions.createDefault();
            var constructor=Class.forName("carpet.patches.EntityPlayerMPFake").getDeclaredConstructor(
                    net.minecraft.server.MinecraftServer.class,net.minecraft.server.world.ServerWorld.class,
                    com.mojang.authlib.GameProfile.class,options.getClass(),boolean.class);
            constructor.setAccessible(true);
            var player=(net.minecraft.server.network.ServerPlayerEntity)constructor.newInstance(server,server.getOverworld(),profile,options,false);
            var connection=(net.minecraft.network.ClientConnection)Class.forName("carpet.patches.FakeClientConnection")
                    .getConstructor(net.minecraft.network.NetworkSide.class).newInstance(net.minecraft.network.NetworkSide.SERVERBOUND);
            server.getPlayerManager().onPlayerConnect(connection,player,new net.minecraft.server.network.ConnectedClientData(profile,0,options,false));
            player.changeGameMode(net.minecraft.world.GameMode.CREATIVE);player.setLoaded(true);
            player.getAbilities().flying=true;player.setNoGravity(true);
            player.refreshPositionAndAngles(90.5,80,4.5,0,0);
            return player;
        }catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
}
