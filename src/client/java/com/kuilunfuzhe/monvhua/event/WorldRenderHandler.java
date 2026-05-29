package com.kuilunfuzhe.monvhua.event;

import com.kuilunfuzhe.monvhua.MonvhuaModClient;
import com.kuilunfuzhe.monvhua.features.evil_eyes.Evil_Eyes;
import com.kuilunfuzhe.monvhua.item.gazeguidance.ModItems;
import com.kuilunfuzhe.monvhua.item.secrecy.SecrecyItem;
import com.kuilunfuzhe.monvhua.renderer.picturerender.AnchorButtonRenderer;
import com.kuilunfuzhe.monvhua.renderer.picturerender.BackTextureRenderer;
import com.kuilunfuzhe.monvhua.renderer.picturerender.OrbitRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

public class WorldRenderHandler {
    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            PlayerEntity player = MinecraftClient.getInstance().player;
            if (player != null) {
                ItemStack mainHand = player.getMainHandStack();
                if (mainHand.getItem() == Evil_Eyes.CLAIRVOYANCE_ITEM || mainHand.getItem() == ModItems.MAGIC_STICK || SecrecyItem.isHoldingSecrecy(mainHand)) {
                    BackTextureRenderer.render(context.matrixStack(), context.consumers(), player, MonvhuaModClient.currentPlayerStage);
                    OrbitRenderer.render(context.matrixStack(), context.consumers(), player, MonvhuaModClient.currentPlayerStage);
                }
            }
            AnchorButtonRenderer.render(context.matrixStack(), context.consumers());
        });
    }
}
