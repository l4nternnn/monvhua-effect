package com.kuilunfuzhe.monvhua.mixin.gravity;

import com.kuilunfuzhe.monvhua.features.gravity.GravityMagic;
import com.kuilunfuzhe.monvhua.features.gravity.SurfaceGravityBasis;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class SurfaceGravityLimbAnimatorMixin {
    @Shadow
    protected abstract void updateLimbs(float posDelta);

    @Inject(method = "updateLimbs(Z)V", at = @At("HEAD"), cancellable = true)
    private void monvhua$updateSurfaceGravityLimbs(boolean flutter, CallbackInfo ci) {
        LivingEntity living = (LivingEntity) (Object) this;
        Entity entity = living;
        Direction downDirection = GravityMagic.getSurfaceGravityDirection(entity);
        if (downDirection == null || downDirection == Direction.DOWN) {
            return;
        }

        if (living.hasVehicle() || !living.isAlive()) {
            this.updateLimbs(0.0F);
            ci.cancel();
            return;
        }

        Vec3d delta = new Vec3d(
                living.getX() - living.lastX,
                living.getY() - living.lastY,
                living.getZ() - living.lastZ
        );
        Vec3d down = SurfaceGravityBasis.directionVector(downDirection);
        float surfaceDistance = (float) SurfaceGravityBasis.reject(delta, down).length();
        this.updateLimbs(surfaceDistance);
        ci.cancel();
    }
}
