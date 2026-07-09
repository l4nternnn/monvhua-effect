package com.kuilunfuzhe.monvhua.mixin.gravity;

import com.kuilunfuzhe.monvhua.features.gravity.SurfaceGravityClientEngine;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GravityCrosshairTargetMixin {
    @Shadow
    @Final
    private MinecraftClient client;

    @Inject(method = "updateCrosshairTarget", at = @At("RETURN"))
    private void monvhua$updateSurfaceGravityCrosshairTarget(float tickProgress, CallbackInfo ci) {
        if (client.player == null || !SurfaceGravityClientEngine.isActive(client.player)) {
            return;
        }
        HitResult hit = SurfaceGravityClientEngine.raycast(client, tickProgress);
        if (hit == null) {
            return;
        }
        client.crosshairTarget = hit;
        Entity target = hit instanceof EntityHitResult entityHit ? entityHit.getEntity() : null;
        client.targetedEntity = target;
    }
}
