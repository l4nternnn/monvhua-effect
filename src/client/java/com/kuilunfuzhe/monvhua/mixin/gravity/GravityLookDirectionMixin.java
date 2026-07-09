package com.kuilunfuzhe.monvhua.mixin.gravity;

import com.kuilunfuzhe.monvhua.features.gravity.GravityMagic;
import com.kuilunfuzhe.monvhua.features.gravity.SurfaceGravityClientEngine;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class GravityLookDirectionMixin {
    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void monvhua$surfaceLookDirection(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (SurfaceGravityClientEngine.isActive(entity)) {
            SurfaceGravityClientEngine.onLookInput(entity, cursorDeltaX, cursorDeltaY);
            ci.cancel();
        }
    }

    @ModifyVariable(method = "changeLookDirection", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private double monvhua$invertInvertedAreaYaw(double cursorDeltaX) {
        MinecraftClient client = MinecraftClient.getInstance();
        Entity entity = (Entity) (Object) this;
        if (SurfaceGravityClientEngine.isActive(entity)) {
            return cursorDeltaX;
        }
        if (client.player == entity && GravityMagic.isInInvertedArea(entity)) {
            return -cursorDeltaX;
        }
        return cursorDeltaX;
    }

    @ModifyVariable(method = "changeLookDirection", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private double monvhua$invertInvertedAreaPitch(double cursorDeltaY) {
        MinecraftClient client = MinecraftClient.getInstance();
        Entity entity = (Entity) (Object) this;
        if (SurfaceGravityClientEngine.isActive(entity)) {
            return cursorDeltaY;
        }
        if (client.player == entity && GravityMagic.isInInvertedArea(entity)) {
            return -cursorDeltaY;
        }
        return cursorDeltaY;
    }
}
