package com.kuilunfuzhe.monvhua.mixin.gravity;

import com.kuilunfuzhe.monvhua.features.gravity.SurfaceGravityClientEngine;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public abstract class SurfaceGravityClientPushOutMixin {
    @Inject(method = "pushOutOfBlocks(DD)V", at = @At("HEAD"), cancellable = true)
    private void monvhua$skipVanillaPushOutForSurfaceGravity(double x, double z, CallbackInfo ci) {
        if (SurfaceGravityClientEngine.isActive((ClientPlayerEntity) (Object) this)) {
            ci.cancel();
        }
    }
}
