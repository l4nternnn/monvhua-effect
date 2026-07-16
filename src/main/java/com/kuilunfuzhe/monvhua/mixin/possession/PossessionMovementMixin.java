package com.kuilunfuzhe.monvhua.mixin.possession;

import com.kuilunfuzhe.monvhua.features.possession.PossessionManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class PossessionMovementMixin {
    @Inject(method = "tickMovement", at = @At("HEAD"))
    private void monvhua$restorePossessionMovement(CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayerEntity target) {
            PossessionManager.prepareTargetMovement(target);
        }
    }
}
