package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.gravity.GravityMagic;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class InvertedPlayerTravelMixin {
    @ModifyVariable(method = "travel", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Vec3d monvhua$correctInvertedFlyingMovementInput(Vec3d movementInput) {
        Entity entity = (Entity) (Object) this;
        if (entity.getWorld().isClient()) {
            return movementInput;
        }
        return GravityMagic.correctInvertedFlyingMovementInput(entity, movementInput);
    }

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void monvhua$cancelVanillaTravelInInvertedArea(Vec3d movementInput, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (entity.getWorld().isClient()) {
            return;
        }

        if (entity instanceof ServerPlayerEntity player && GravityMagic.tickInvertedPlayer(player, player.getPlayerInput())) {
            ci.cancel();
            return;
        }

        if (GravityMagic.cancelServerInvertedTravel(entity)) {
            ci.cancel();
        }
    }
}
