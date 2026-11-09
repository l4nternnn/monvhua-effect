package com.kuilunfuzhe.monvhua.features.physics_preview;

import com.kuilunfuzhe.monvhua.features.forensics.*;
import com.kuilunfuzhe.monvhua.features.forensics.physics.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;

import java.util.UUID;
import static com.kuilunfuzhe.monvhua.features.physics_preview.PhysicsPreviewChecks.require;

final class CorpseHistoryGameChecks {
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        world.getServer().runOnServer(s->{
            for(var d:s.getOverworld().getEntitiesByClass(ItemDisplayEntity.class,new net.minecraft.util.math.Box(-12,78,-12,12,90,12),x->Forensics.hasBinding(x.getItemStack())))d.discard();
        });
        world.getServer().runCommand("gamemode creative @a");
        world.getServer().runCommand("tp @a 0.5 80 5.5 180 10");context.waitTicks(4);
        context.runOnClient(c->c.getNetworkHandler().sendChatCommand("mvcorpse physics test-motion front"));
        context.waitFor(c->CorpseHistoryController.entries().stream().anyMatch(e->e.active&&e.last()>=12),80);
        UUID[] id={null};
        world.getServer().runOnServer(s->{
            var e=CorpseHistoryController.entries().stream().filter(x->x.active).findFirst().orElseThrow();id[0]=e.id;
            var p=s.getPlayerManager().getPlayerList().getFirst();p.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,new net.minecraft.item.ItemStack(CorpseHistoryController.History_Cortol_ITEM));
            aim(p,e);
        });
        context.waitTicks(2);
        context.runOnClient(c->require(CorpseControllerClient.attack(),"Controller failed to pick live corpse"));
        context.waitFor(c->entry(id).manual,60);
        context.waitFor(c->entry(id).confirmed(),80);
        long[] frozen={0};context.runOnClient(c->frozen[0]=entry(id).last());
        CorpseProfileGameChecks.whileFrozen(context,world);
        context.waitTicks(250);
        context.runOnClient(c->{var e=entry(id);require(e.valid&&e.active&&e.last()==frozen[0],"Frozen session timed out or advanced");});
        world.getServer().runOnServer(s->{var p=s.getPlayerManager().getPlayerList().getFirst();aim(p,entry(id));});context.waitTicks(2);
        context.runOnClient(c->c.options.sneakKey.setPressed(true));context.waitTicks(2);
        context.runOnClient(c->require(CorpseControllerClient.use(),"Sneak use was not handled"));
        context.waitFor(c->c.currentScreen instanceof CorpseTimelineScreen,60);
        context.runOnClient(c->c.options.sneakKey.setPressed(false));
        context.waitTicks(3);
        context.runOnClient(c->{
            require(!c.currentScreen.shouldPause(),"Timeline pauses world");
            require(entry(id).session.historyHz()==12,"Wrong history sampling rate");
            require(entry(id).steps().stream().allMatch(step->step%5==0||step==entry(id).last()),"Off-grid history sample");
            var screen=c.currentScreen;screen.mouseClicked(screen.width/2.0,screen.height-70,0);
            require(entry(id).selectedFrame>entry(id).first(),"Timeline click did not select middle frame");
            screen.mouseDragged(screen.width-16,screen.height-70,0,1,0);screen.mouseReleased(screen.width-16,screen.height-70,0);
            require(entry(id).selectedFrame==entry(id).last(),"Timeline drag did not reach last frame");
            screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_HOME,0,0);
            screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT,0,0);
            require(entry(id).selectedFrame==entry(id).steps().get(1),"Keyboard did not advance to actual sparse sample");
            screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_HOME,0,0);
        });
        context.takeScreenshot("physics-history-transparent-first-frame");
        context.runOnClient(c->c.currentScreen.close());context.waitTicks(3);
        context.runOnClient(c->{var e=entry(id);require(e.manual&&!e.timeline&&e.last()==frozen[0],"Closing timeline cleared manual freeze");e.selectedFrame=-1;});
        world.getServer().runOnServer(s->{var p=s.getPlayerManager().getPlayerList().getFirst();aim(p,entry(id));CorpseHistoryController.operate(p,id[0],false);});
        context.waitFor(c->!entry(id).active,260);
        context.runOnClient(c->{
            var samples=LiveCorpseRenderer.diagnosticSamples(id[0]);require(samples.size()>20,"Missing live render trace");
            LiveCorpseRenderer.DiagnosticSample previous=null;var csv=new StringBuilder("nanos,display_step,latest_step,mode,waits,resyncs\n");
            for(var sample:samples){
                require(sample.displayed()<=sample.latest(),"Renderer extrapolated into the future");
                if(previous!=null&&previous.mode().equals("RUN")&&sample.mode().equals("RUN"))require(sample.displayed()+1e-6>=previous.displayed(),"Real render clock rewound");
                csv.append(sample.nanos()).append(',').append(sample.displayed()).append(',').append(sample.latest()).append(',').append(sample.mode()).append(',').append(sample.waits()).append(',').append(sample.resyncs()).append('\n');previous=sample;
            }
            try{java.nio.file.Files.writeString(PhysicsPreviewClient.instance.directory().getParent().resolve("physics-playback-trace.csv"),csv.toString());}catch(java.io.IOException e){throw new AssertionError(e);}
        });
        net.minecraft.nbt.NbtCompound[] saved={null};
        world.getServer().runOnServer(s->{
            var e=entry(id);var d=(ItemDisplayEntity)s.getOverworld().getEntity(e.id);saved[0]=CorpsePoseRecord.data(d.getItemStack());
            require(saved[0].contains(CorpsePoseRecord.KEY),"Resumed corpse did not settle");
            var diagnostics=ForensicStore.get(s).corpse(Forensics.binding(d.getItemStack()).getString("id","")).getCompoundOrEmpty("pose_generation_diagnostics");
            require(diagnostics.getString("profile_hash","").equals(CorpsePhysicsProfiles.F5.hash()),"Running request changed profile during reload");
            var p=s.getPlayerManager().getPlayerList().getFirst();aim(p,e);CorpseHistoryController.operate(p,e.id,true);
            s.getCommandManager().executeWithPrefix(p.getCommandSource(),"mvcorpse physics history duration selected 60");
            require(e.seconds==60,"History duration command failed");
        });
        context.runOnClient(c->{c.setScreen(new CorpseTimelineScreen(entry(id),c.getServer(),c.player.getUuid()));c.currentScreen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_HOME,0,0);});
        context.waitTicks(3);context.takeScreenshot("physics-history-settled-rewind");
        context.runOnClient(c->c.currentScreen.close());context.waitTicks(3);
        world.getServer().runOnServer(s->{
            var d=(ItemDisplayEntity)s.getOverworld().getEntity(id[0]);require(saved[0].equals(CorpsePoseRecord.data(d.getItemStack())),"History mutated static corpse");
            d.discard();
        });
        context.waitTicks(2);require(CorpseHistoryController.get(id[0])==null,"Deleted corpse retained history");
        try{java.nio.file.Files.writeString(PhysicsPreviewClient.instance.directory().getParent().resolve("physics-history-test.txt"),"PASS: worker-confirmed freeze >240 world ticks, transparent non-pausing timeline, manual freeze survives screen close, resume/settle, terminal rewind does not mutate corpse, duration command and deletion cleanup.\n");}
        catch(java.io.IOException e){throw new AssertionError(e);}
    }
    private static CorpseHistoryController.Entry entry(UUID[] id){var e=CorpseHistoryController.get(id[0]);if(e==null)throw new AssertionError("Missing history "+id[0]);return e;}
    private static void aim(net.minecraft.server.network.ServerPlayerEntity player,CorpseHistoryController.Entry e){
        var frames=e.session.frames();var p=frames.getLast().poses().get("torso");
        var target=e.view.origin().add(p.x(),p.y(),p.z());
        // Keep the eye within the normal reach even when the corpse falls away from its origin.
        player.requestTeleport(target.x,80,target.z+2);
        player.lookAt(net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES,target);
    }
}
