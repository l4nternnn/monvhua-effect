package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.item.through.ThroughItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class ThroughItemUsePriorityMixin {
    @Shadow
    private int itemUseCooldown;

    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void monvhua$prioritizeThroughItemUse(CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        if (client.player == null || client.interactionManager == null) {
            return;
        }
        if (!ThroughItem.isHoldingSecrecy(client.player.getMainHandStack())) {
            return;
        }

        ActionResult result = client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
        if (!result.isAccepted()) {
            return;
        }
        itemUseCooldown = 4;
        ci.cancel();
    }
}
