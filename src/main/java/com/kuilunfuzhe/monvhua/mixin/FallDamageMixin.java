package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.floating.floating;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(PlayerEntity.class)
public class FallDamageMixin {

    @ModifyVariable(method = "handleFallDamage", at = @At("HEAD"), argsOnly = true)
    private float onHandleFallDamage(float fallDistance) {
        if (floating.isFloating()) {
            return 0;
        }
        return fallDistance;
    }
}