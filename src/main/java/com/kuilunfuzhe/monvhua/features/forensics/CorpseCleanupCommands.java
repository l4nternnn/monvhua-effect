package com.kuilunfuzhe.monvhua.features.forensics;

import com.kuilunfuzhe.monvhua.features.block.body.BodyPartManager;
import com.kuilunfuzhe.monvhua.features.forensics.physics.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.entity.decoration.InteractionEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.util.*;
import static net.minecraft.server.command.CommandManager.*;
import static com.mojang.brigadier.arguments.DoubleArgumentType.*;

/** Explicit administrator cleanup; includes legacy carriers without the newer cleanup tags. */
public final class CorpseCleanupCommands {
    private static final String LINK="monvhua_corpse_display:";
    public static void initialize(){
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->{
            var root=literal("cleanup").requires(s->s.hasPermissionLevel(2));
            for(boolean confirm:new boolean[]{false,true}){
                String action=confirm?"confirm":"preview";
                root.then(literal("world").then(literal(action).executes(c->run(c.getSource(),false,-1,confirm))));
                root.then(literal("all").then(literal(action).executes(c->run(c.getSource(),true,-1,confirm))));
                root.then(literal("near").then(argument("radius",doubleArg(0,2048)).then(literal(action)
                        .executes(c->run(c.getSource(),false,getDouble(c,"radius"),confirm)))));
            }
            dispatcher.register(literal("mvcorpse").then(root));
        });
    }
    static boolean legacyDisplay(boolean proxy,boolean tagged,boolean bound,boolean bodyItem,boolean combined,boolean linked){
        return !proxy&&(tagged||bound||bodyItem||combined||linked);
    }
    private static boolean corpse(ItemDisplayEntity display){
        return legacyDisplay(display.getCommandTags().contains(KomDyingCompat.MARKER)||KomDyingCompat.proxy(display.getItemStack()),
                display.getCommandTags().contains("monvhua_corpse"),Forensics.hasBinding(display.getItemStack()),
                BodyPartManager.BODY_PART_DISPLAY_ITEMS.contains(display.getItemStack().getItem()),
                Identifier.of("monvhua","combined_body").equals(display.getItemStack().get(DataComponentTypes.ITEM_MODEL)),
                BodyPartManager.DISPLAY_TO_INTERACTION.containsKey(display.getUuid()));
    }
    private static UUID linked(InteractionEntity interaction){
        var mapped=BodyPartManager.INTERACTION_TO_DISPLAY.get(interaction.getUuid());if(mapped!=null)return mapped;
        for(var tag:interaction.getCommandTags())if(tag.startsWith(LINK))try{return UUID.fromString(tag.substring(LINK.length()));}catch(IllegalArgumentException ignored){}
        return null;
    }
    private static int run(ServerCommandSource source,boolean all,double radius,boolean confirm){
        int bodies=0,interactions=0,skipped=0;
        var worlds=new ArrayList<ServerWorld>();
        if(all)source.getServer().getWorlds().forEach(worlds::add);else worlds.add(source.getWorld());
        for(var world:worlds){
            // Scan actual loaded entities, not just the new index: old untagged bodies must be discoverable.
            var displays=new HashMap<UUID,ItemDisplayEntity>();var controls=new ArrayList<InteractionEntity>();
            for(var entity:world.iterateEntities()){
                if(entity.isRemoved())continue;
                if(entity instanceof ItemDisplayEntity display&&corpse(display))displays.put(display.getUuid(),display);
                else if(entity instanceof InteractionEntity interaction)controls.add(interaction);
            }
            var selected=new HashSet<UUID>();var busy=new HashSet<UUID>();
            for(var display:displays.values()){
                if(radius>=0&&display.getPos().squaredDistanceTo(source.getPosition())>radius*radius)continue;
                if(CorpseSeparationService.busy(display.getUuid())){busy.add(display.getUuid());skipped++;continue;}
                selected.add(display.getUuid());
            }
            var removeControls=new ArrayList<InteractionEntity>();
            for(var interaction:controls){
                UUID id=linked(interaction);if(id!=null&&busy.contains(id))continue;
                Entity owner=id==null?null:world.getEntity(id);
                if(owner instanceof ItemDisplayEntity display&&!corpse(display))continue;
                if(selected.contains(id)||((owner==null||owner.isRemoved())
                        &&(id!=null||interaction.getCommandTags().contains("monvhua_corpse"))
                        &&(radius<0||interaction.getPos().squaredDistanceTo(source.getPosition())<=radius*radius)))removeControls.add(interaction);
            }
            bodies+=selected.size();interactions+=removeControls.size();
            if(!confirm)continue;
            for(var id:selected){
                var display=displays.get(id);
                CorpseGrabController.releaseTarget(id);CorpseTransportController.releaseTarget(id);
                CorpseCarryService.forgetForCleanup(display);
                CorpsePhysicsCoordinator.cancel(display,"管理员清除遗体");CorpseHistoryController.remove(id);
                for(var viewer:source.getServer().getPlayerManager().getPlayerList())
                    if(viewer.currentScreenHandler instanceof com.kuilunfuzhe.monvhua.screen.BodyPartScreenHandler screen
                            &&screen.views(BodyPartManager.BODY_PART_DISPLAY_INVENTORIES.get(id)))viewer.closeHandledScreen();
                // Retire only the current carrier. Leave dossier, observations and cards intact.
                if(Forensics.hasBinding(display.getItemStack())&&Forensics.valid(world,display.getItemStack(),id.toString()))Forensics.retire(world,display.getItemStack());
                Forensics.discardPair(world,display);CorpseLoadedIndex.unload(display);
            }
            for(var interaction:removeControls){
                var id=BodyPartManager.INTERACTION_TO_DISPLAY.remove(interaction.getUuid());
                if(id!=null)BodyPartManager.DISPLAY_TO_INTERACTION.remove(id,interaction.getUuid());
                interaction.discard();
            }
        }
        final int count=bodies,links=interactions,waiting=skipped;
        source.sendFeedback(()->Text.literal((confirm?"已清除":"预览可清除")+"遗体/肢体模型 "+count+" 个，交互实体 "+links+" 个；跳过正在分离的载体 "+waiting+" 个。仅统计已加载实体，背包随载体删除，不掉落物品。"),confirm);
        return bodies+interactions;
    }
}
