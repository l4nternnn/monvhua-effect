package com.kuilunfuzhe.monvhua.mixin.possession;

import com.kuilunfuzhe.monvhua.features.possession.PossessionClient;
import com.kuilunfuzhe.monvhua.features.possession.PossessionPackets;
import com.kuilunfuzhe.monvhua.network.SafeClientNetworking;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class PossessionUseReleaseMixin {
    @Inject(method = "stopUsingItem", at = @At("HEAD"), cancellable = true)
    private void monvhua$releasePossessedItem(PlayerEntity player, CallbackInfo ci) {
        if (!PossessionClient.isActive()) {
            return;
        }
        SafeClientNetworking.send(new PossessionPackets.ActionC2S(PossessionPackets.ActionC2S.RELEASE_USE));
        ci.cancel();
    }
}
