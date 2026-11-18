package com.kuilunfuzhe.monvhua.features.forensics.cleaning;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.item.*;
import net.minecraft.potion.Potions;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;

/** A deliberate wash of the hit block and its one-block neighbourhood. */
public final class WaterCleaning {
    private WaterCleaning(){}
    public static void initialize(){
        UseBlockCallback.EVENT.register((player,world,hand,hit)->{
            var stack=player.getStackInHand(hand);
            boolean bucket=stack.isOf(Items.WATER_BUCKET);
            boolean bottle=stack.isOf(Items.POTION)&&stack.getOrDefault(DataComponentTypes.POTION_CONTENTS,PotionContentsComponent.DEFAULT).matches(Potions.WATER);
            if((!bucket&&!bottle)||player.isSpectator()||!player.isAlive())return ActionResult.PASS;
            if(!(world instanceof ServerWorld server))return ActionResult.PASS;
            var result=ForensicCleaningApi.washBlockRing(server,hit.getBlockPos(),bottle);
            if(result.status()!=ForensicCleaningApi.Status.APPLIED)return ActionResult.PASS;
            // Only a successful wash consumes water; otherwise vanilla bucket/bottle use continues.
            player.setStackInHand(hand,ItemUsage.exchangeStack(stack,player,new ItemStack(bucket?Items.BUCKET:Items.GLASS_BOTTLE)));
            player.sendMessage(Text.literal(bucket?"已冲洗周围血迹":"已用水瓶冲洗；新鲜血迹需要冲洗两次"),true);
            return ActionResult.SUCCESS;
        });
    }
}
