package com.kuilunfuzhe.monvhua.mixin.possession;

import com.kuilunfuzhe.monvhua.features.possession.PossessionClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class PossessionHandStackMixin {
    @Inject(method = "getMainHandStack", at = @At("HEAD"), cancellable = true)
    private void monvhua$mirrorPossessedMainHand(CallbackInfoReturnable<ItemStack> cir) {
        if (PossessionClient.shouldMirrorHandsFor(this)) {
            cir.setReturnValue(PossessionClient.getMirroredMainHandStack());
        }
    }

    @Inject(method = "getOffHandStack", at = @At("HEAD"), cancellable = true)
    private void monvhua$mirrorPossessedOffHand(CallbackInfoReturnable<ItemStack> cir) {
        if (PossessionClient.shouldMirrorHandsFor(this)) {
            cir.setReturnValue(PossessionClient.getMirroredOffHandStack());
        }
    }
}
