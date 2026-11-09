package com.kuilunfuzhe.monvhua.features.physics_preview;

import com.kuilunfuzhe.monvhua.features.forensics.*;
import com.kuilunfuzhe.monvhua.features.forensics.physics.*;
import com.kuilunfuzhe.monvhua.features.block.body.BodyPartManager;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.block.*;
import net.minecraft.util.math.*;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import java.util.*;
import java.util.concurrent.*;
import java.nio.file.*;
import static com.kuilunfuzhe.monvhua.features.physics_preview.PhysicsPreviewChecks.require;

/** Actual block collision sampling and static commit; distinct from the native idealized matrix. */
final class CorpseTerrainGameChecks {
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        var rows=new ArrayList<String>();
        world.getServer().runCommand("gamemode creative @a");
        for(String scene:List.of("floor","slab_platform","isolated_slab","stairs_edge","wall","corner")){
            var origin=new Vec3d(.35,scene.equals("slab_platform")||scene.equals("isolated_slab")?79.5:80,.35);
            world.getServer().runOnServer(server->{
                var w=server.getOverworld();
                for(BlockPos p:BlockPos.iterate(-5,79,-5,5,83,5))w.setBlockState(p,p.getY()==79?Blocks.STONE.getDefaultState():Blocks.AIR.getDefaultState());
                if(scene.equals("slab_platform"))for(BlockPos p:BlockPos.iterate(-5,79,-5,5,79,5))w.setBlockState(p,Blocks.STONE_SLAB.getDefaultState());
                if(scene.equals("isolated_slab")){
                    for(BlockPos p:BlockPos.iterate(-5,79,-5,5,79,5))w.setBlockState(p,Blocks.AIR.getDefaultState());
                    for(BlockPos p:BlockPos.iterate(-5,78,-5,5,78,5))w.setBlockState(p,Blocks.STONE.getDefaultState());
                    w.setBlockState(new BlockPos(0,79,0),Blocks.STONE_SLAB.getDefaultState());
                }
                if(scene.equals("stairs_edge"))for(int x=-1;x<=1;x++)w.setBlockState(new BlockPos(x,80,1),Blocks.STONE_STAIRS.getDefaultState().with(StairsBlock.FACING,Direction.SOUTH));
                if(scene.equals("wall")||scene.equals("corner"))for(BlockPos p:BlockPos.iterate(-5,80,1,5,82,1))w.setBlockState(p,Blocks.STONE.getDefaultState());
                if(scene.equals("corner"))for(BlockPos p:BlockPos.iterate(1,80,-5,1,82,0))w.setBlockState(p,Blocks.STONE.getDefaultState());
            });
            for(boolean slim:new boolean[]{false,true})for(int angle:new int[]{0,90,180,270}){
                ItemDisplayEntity[] carrier={null};var future=new java.util.concurrent.atomic.AtomicReference<CompletableFuture<PhysicsPoseDocument>>();
                world.getServer().runOnServer(server->{
                    var player=server.getPlayerManager().getPlayerList().getFirst();player.setPosition(.5,82,3);player.setYaw(180);player.setPitch(0);
                    carrier[0]=CorpsePoseCommands.createPhysicsTest(player.getCommandSource().withLevel(4));require(carrier[0]!=null,"Terrain carrier missing");carrier[0].setPosition(origin);
                    float dx=(float)Math.sin(Math.toRadians(angle)),dz=(float)Math.cos(Math.toRadians(angle));
                    var request=new CorpsePhysicsBridge.Request(slim,0,.4f,CorpseTerrainSnapshot.capture(server.getOverworld(),origin),new CorpsePhysicsBridge.Motion(dx*.4f,dz*.4f,dx,dz,.5f,.6f),CorpsePhysicsProfiles.F5);
                    future.set(CorpsePhysicsCoordinator.diagnosticProvider(server).solve(request));
                });
                PhysicsPoseDocument result;
                try{result=future.get().get(20,TimeUnit.SECONDS);}catch(Exception e){throw new AssertionError(scene+" initialization failed",e);}
                world.getServer().runOnServer(server->{
                    var display=carrier[0];String stage=result.outcome();
                    if(stage.equals("SETTLED")){
                        var before=display.getItemStack().copy();
                        try{
                            CorpsePoseService.applyGenerated(display,new CorpsePoseRecord(1,"generated",slim?"slim":"default",result.poses(),result.definition()),origin);
                            stage="APPLIED";var data=CorpsePoseRecord.data(display.getItemStack());
                            require(CorpsePoseGeometry.clear(server.getOverworld(),display,data,origin)&&CorpsePoseGeometry.supported(server.getOverworld(),display,data,origin),"Committed invalid terrain pose");
                        }catch(IllegalArgumentException rejected){require(net.minecraft.item.ItemStack.areEqual(before,display.getItemStack()),"Rejected terrain commit mutated item");stage="STATIC_COMMIT_REJECTED";}
                    }
                    if(scene.equals("floor")||scene.equals("slab_platform"))require(stage.equals("APPLIED"),"Flat platform regression: "+scene+" "+stage);
                    rows.add(scene+" "+(slim?"slim":"standard")+" "+angle+" solver="+result.outcome()+" host="+stage+" step="+result.step());
                    var linked=BodyPartManager.DISPLAY_TO_INTERACTION.get(display.getUuid());var interaction=linked==null?null:server.getOverworld().getEntity(linked);if(interaction!=null)interaction.discard();display.discard();
                });
            }
        }
        world.getServer().runOnServer(server->{for(BlockPos p:BlockPos.iterate(-5,79,-5,5,83,5))server.getOverworld().setBlockState(p,p.getY()==79?Blocks.STONE.getDefaultState():Blocks.AIR.getDefaultState());});
        try{Files.writeString(PhysicsPreviewClient.instance.directory().getParent().resolve("physics-g3-block-terrain.txt"),String.join("\n",rows)+"\n");}catch(java.io.IOException e){throw new AssertionError(e);}
    }
}
