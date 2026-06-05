package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.kuilunfuzhe.monvhua.features.floating.floating;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(PlayerEntity.class)
public class FallDamageMixin {

    @ModifyVariable(method = "handleFallDamage", at = @At("HEAD"), argsOnly = true)
    private float onHandleFallDamage(float fallDistance) {
//        MonvhuaMod.LOGGER.info("注入生效，开始判断");
        if (floating.isFloating()) {
//            MonvhuaMod.LOGGER.info("注入生效，当前伤害归零");
            return 0;
        }
        return fallDistance;
    }
}