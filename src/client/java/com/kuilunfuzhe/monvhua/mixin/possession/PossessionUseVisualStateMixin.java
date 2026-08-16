package com.kuilunfuzhe.monvhua.mixin.possession;

import com.kuilunfuzhe.monvhua.features.possession.PossessionClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientPlayerEntity.class)
public abstract class PossessionUseVisualStateMixin {
    @Redirect(
            method = "onTrackedDataSet",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/network/ClientPlayerEntity;clearActiveItem()V"
            )
    )
    private void monvhua$preservePossessionUseVisual(ClientPlayerEntity player) {
        if (!PossessionClient.isVisualUsing()) {
            player.clearActiveItem();
        }
    }
}
