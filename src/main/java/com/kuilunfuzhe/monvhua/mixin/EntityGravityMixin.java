package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.gravity.GravityMagic;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityGravityMixin {
    @Inject(method = "getGravity", at = @At("HEAD"), cancellable = true)
    private void monvhua$overrideGravity(CallbackInfoReturnable<Double> cir) {
        Entity entity = (Entity) (Object) this;
        if (GravityMagic.shouldSuppressVanillaGravity(entity)) {
            cir.setReturnValue(0.0D);
        }
    }
}
