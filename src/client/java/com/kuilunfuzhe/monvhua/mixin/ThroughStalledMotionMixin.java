package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.through.ThroughClientManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class ThroughStalledMotionMixin {
    @Inject(method = "travel", at = @At("HEAD"))
    private void monvhua$stopStalledMotionBeforeTravel(Vec3d movementInput, CallbackInfo ci) {
        if ((Object) this == MinecraftClient.getInstance().player) {
            ThroughClientManager.stopMotionWhenPhaseMovementStops();
        }
    }

    @Inject(method = "travel", at = @At("TAIL"))
    private void monvhua$stopStalledMotionAfterTravel(Vec3d movementInput, CallbackInfo ci) {
        if ((Object) this == MinecraftClient.getInstance().player) {
            ThroughClientManager.stopMotionWhenPhaseMovementStops();
        }
    }

    @Inject(method = "tickMovement", at = @At("HEAD"))
    private void monvhua$stopStalledMotionBeforeTickMovement(CallbackInfo ci) {
        if ((Object) this == MinecraftClient.getInstance().player) {
            ThroughClientManager.stopMotionWhenPhaseMovementStops();
        }
    }

    @Inject(method = "tickMovement", at = @At("TAIL"))
    private void monvhua$stopStalledMotionAfterTickMovement(CallbackInfo ci) {
        if ((Object) this == MinecraftClient.getInstance().player) {
            ThroughClientManager.stopMotionWhenPhaseMovementStops();
        }
    }
}
