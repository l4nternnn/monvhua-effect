package com.kuilunfuzhe.monvhua.features.forensics.cards;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.world.World;

public final class ClueCardItems {
    public static final Item BLANK=create("clue_blank_card",64,0);
    public static final Item CARD=create("clue_record_card",1,1);
    public static final Item BAG=create("clue_card_bag",1,2);
    public static final Item MANAGER=create("clue_case_manager",1,3);
    private ClueCardItems(){}
    private static Item create(String name,int count,int kind){var id=Identifier.of("monvhua",name);var key=RegistryKey.of(RegistryKeys.ITEM,id);return new CardItem(new Item.Settings().registryKey(key).maxCount(count),kind);}
    public static void initialize(){
        Registry.register(Registries.ITEM,Identifier.of("monvhua","clue_blank_card"),BLANK);
        Registry.register(Registries.ITEM,Identifier.of("monvhua","clue_record_card"),CARD);
        Registry.register(Registries.ITEM,Identifier.of("monvhua","clue_card_bag"),BAG);
        Registry.register(Registries.ITEM,Identifier.of("monvhua","clue_case_manager"),MANAGER);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(e->{e.add(BLANK);e.add(CARD);e.add(BAG);e.add(MANAGER);});
    }
    public static String data(ItemStack stack,String key){return stack.getOrDefault(DataComponentTypes.CUSTOM_DATA,NbtComponent.DEFAULT).copyNbt().getString(key,"");}
    public static ItemStack blanks(String session){return blanks(session,8);}
    public static ItemStack blanks(String session,int count){var s=new ItemStack(BLANK,count);var n=new NbtCompound();n.putString("clue_session",session);s.set(DataComponentTypes.CUSTOM_DATA,NbtComponent.of(n));return s;}
    public static ItemStack card(String id){var s=new ItemStack(CARD);var n=new NbtCompound();n.putString("clue_id",id);s.set(DataComponentTypes.CUSTOM_DATA,NbtComponent.of(n));return s;}
    private static final class CardItem extends Item {
        private final int kind;
        private CardItem(Settings settings,int kind){super(settings);this.kind=kind;}
        @Override public ActionResult use(World world,PlayerEntity player,Hand hand){if(player instanceof ServerPlayerEntity server)act(server,hand);return ActionResult.SUCCESS;}
        @Override public ActionResult useOnBlock(ItemUsageContext context){if(context.getPlayer() instanceof ServerPlayerEntity server)act(server,context.getHand());return ActionResult.SUCCESS;}
        private void act(ServerPlayerEntity p,Hand hand){
            if(hand!=Hand.MAIN_HAND)return;
            try{
                if(kind==0){if(p.isSneaking())ClueCardService.propose(p);else p.sendMessage(Text.literal("对准位置潜行右键可记录；打开卡袋可整理记录"),true);}
                else if(kind==3)ClueCaseAdmin.open(p);
                else if(kind==2)ClueCardService.send(p,"","",true,false);
                else {String id=data(p.getStackInHand(hand),"clue_id");if(p.isSneaking())ClueCardService.deposit(p,id);else ClueCardService.openCard(p,id);}
            }catch(IllegalArgumentException e){p.sendMessage(Text.literal(e.getMessage()),false);}
        }
    }
}
