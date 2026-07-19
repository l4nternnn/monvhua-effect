package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.gravity.GravityMagic;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class InvertedSneakLedgeClipMixin {
    @Inject(method = "clipAtLedge", at = @At("HEAD"), cancellable = true)
    private void monvhua$disableNormalSneakLedgeClipInInvertedArea(CallbackInfoReturnable<Boolean> cir) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        if (GravityMagic.isInInvertedArea(player) || GravityMagic.hasNonNormalSurfaceGravity(player)) {
            cir.setReturnValue(false);
        }
    }
}
