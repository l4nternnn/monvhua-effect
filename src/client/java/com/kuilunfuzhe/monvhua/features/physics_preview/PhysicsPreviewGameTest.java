package com.kuilunfuzhe.monvhua.features.physics_preview;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.command.v2.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Dedicated temporary world; enabled only by -PphysicsPreviewGameTest=true. */
public final class PhysicsPreviewGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var preview=PhysicsPreviewClient.instance;
        try {
            Files.createDirectories(preview.directory());
            for(String name:List.of("floor","slab","stairs","neutral-0","neutral-90","neutral-180","neutral-270")) {
                try(var in=getClass().getResourceAsStream("/assets/monvhua/physics_preview/fixtures/"+name+".json")) {
                    if(in==null)throw new AssertionError("Missing fixture "+name);
                    Files.write(preview.directory().resolve(name+".json"),in.readAllBytes());
                }
            }
            PhysicsPreviewChecks.invalidInputs(Files.readString(preview.directory().resolve("floor.json")));
            try{PhysicsPreviewFiles.read(preview.directory(),"../floor.json");throw new AssertionError("Traversal accepted");}catch(IllegalArgumentException expected){}
        }catch(java.io.IOException e){throw new AssertionError(e);}
        try(var world=context.worldBuilder().create()) {
            world.getServer().runOnServer(server->{
                for(BlockPos pos:BlockPos.iterate(-8,79,-8,8,79,8))server.getOverworld().setBlockState(pos,Blocks.STONE.getDefaultState());
            });
            world.getServer().runCommand("tp @a 0.5 80 5.5 180 10");
            context.waitTicks(20);world.getClientWorld().waitForChunksRender();
            for(String name:List.of("neutral-0","neutral-90","neutral-180","neutral-270","floor","slab","stairs")) {
                context.runOnClient(client->{preview.clear();execute("mvphysics preview skin slim");execute("mvphysics preview load "+name+".json");});
                context.waitFor(client->preview.diagnosticDocument()!=null,200);
                context.runOnClient(client->{
                    PhysicsPreviewChecks.check(preview.diagnosticDocument(),preview.diagnosticSurfaces(),true);
                    if(name.equals("neutral-0")) {
                        var baked=new com.kuilunfuzhe.monvhua.renderer.body.special.CombinedBodySpecialModelRenderer(client.getLoadedEntityModels());
                        var texture=net.minecraft.util.Identifier.ofVanilla("textures/entity/player/wide/steve.png");
                        var data=new com.kuilunfuzhe.monvhua.renderer.body.special.BodyPartSpecialModelRenderer.Data(net.minecraft.client.render.RenderLayer.getEntityCutoutNoCull(texture),texture,"default",new net.minecraft.nbt.NbtCompound());
                        for(boolean slim:new boolean[]{false,true}) {
                        var shapeData=new com.kuilunfuzhe.monvhua.renderer.body.special.BodyPartSpecialModelRenderer.Data(data.layer(),data.texture(),slim?"slim":"default",new net.minecraft.nbt.NbtCompound());
                        for(String part:PhysicsPoseDocument.IDS)for(int axis=0;axis<3;axis++)for(int sign:new int[]{-1,1}) {
                            var poses=new java.util.HashMap<>(preview.diagnosticDocument().poses());var p=poses.get(part);
                            var q=new org.joml.Quaternionf().rotationAxis(sign*(float)Math.PI/2,axis==0?1:0,axis==1?1:0,axis==2?1:0);
                            poses.put(part,new PhysicsPoseDocument.Pose(p.x(),p.y(),p.z(),q.x,q.y,q.z,q.w));
                            var synthetic=new PhysicsPoseDocument("TIMEOUT",0,poses,0,"Geometry-only single-axis fixture");
                            PhysicsPreviewChecks.check(synthetic,baked.physicsPreviewSurfaces(StandardBodyPhysicsAdapter.matrices(synthetic,slim),shapeData),slim);
                        }
                        }
                    }
                });
                if(name.equals("neutral-0")||name.equals("floor")) {
                    if(name.equals("floor")) {world.getServer().runCommand("tp @a 0.5 82 7.5 180 45");context.waitTicks(5);}
                    context.runOnClient(client->{execute("mvphysics preview marks on");client.options.hudHidden=true;});
                    context.waitTicks(3);
                    try {
                        context.takeScreenshot("physics-model-"+name);
                        if(name.equals("floor")) {
                            context.runOnClient(client->{execute("mvphysics preview skin steve");execute("mvphysics preview mode model");});
                            context.waitTicks(3);context.takeScreenshot("physics-steve-floor");
                            context.runOnClient(client->{execute("mvphysics preview skin debug");execute("mvphysics preview mode overlay");});
                        }
                    }finally{context.runOnClient(client->client.options.hudHidden=false);}
                    if(name.equals("floor")){world.getServer().runCommand("tp @a 0.5 80 5.5 180 10");context.waitTicks(5);}
                }
            }
            CorpsePoseGameChecks.run(context,world);
            if(Boolean.getBoolean("monvhua.physicsAutomaticGameTest"))CorpseAutomaticGameChecks.run(context,world);
            if(Boolean.getBoolean("monvhua.physicsMissingCoreGameTest"))CorpseAutomaticGameChecks.missing(context,world);
            // New failed imports must retain the previous valid scene.
            final PhysicsPoseDocument[] prior={null};
            context.runOnClient(client->{prior[0]=preview.diagnosticDocument();execute("mvphysics preview load missing.json");});
            context.waitTicks(10);
            context.runOnClient(client->PhysicsPreviewChecks.require(preview.diagnosticDocument()==prior[0],"Failed load destroyed scene"));
            for(int i=0;i<100;i++) {
                context.runOnClient(client->{preview.clear();execute("mvphysics preview load floor.json");});
                context.waitFor(client->preview.diagnosticDocument()!=null,200);
            }
            context.runOnClient(client->{execute("mvphysics preview load stairs.json");preview.clear();});
            context.waitTicks(10);
            context.runOnClient(client->PhysicsPreviewChecks.require(preview.diagnosticDocument()==null,"Cancelled load repopulated scene"));
            context.runOnClient(client->execute("mvphysics preview load floor.json"));
            context.waitFor(client->preview.diagnosticDocument()!=null,200);
        }
        context.runOnClient(client->PhysicsPreviewChecks.require(preview.diagnosticDocument()==null,"Disconnect retained preview"));
        try{Files.writeString(preview.directory().getParent().resolve("physics-preview-test.txt"),"PASS: seven slim poses + 72 slim/wide single-axis variants + corpse integration, 11 surfaces x six faces, rigid bounds, inverse matrices, invalid imports, 100 reloads, cancellation and disconnect.\n",StandardCharsets.UTF_8);}
        catch(java.io.IOException e){throw new AssertionError(e);}
    }
    private static void execute(String command) {
        var client=MinecraftClient.getInstance();
        try {int result=ClientCommandManager.getActiveDispatcher().execute(command,(FabricClientCommandSource)client.getNetworkHandler().getCommandSource());if(result!=1)throw new AssertionError("Command failed: "+command);}
        catch(com.mojang.brigadier.exceptions.CommandSyntaxException e){throw new AssertionError(e);}
    }
}
