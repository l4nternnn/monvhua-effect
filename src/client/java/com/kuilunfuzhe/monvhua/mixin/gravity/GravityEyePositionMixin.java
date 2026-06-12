package com.kuilunfuzhe.monvhua.mixin.gravity;

import com.kuilunfuzhe.monvhua.features.gravity.GravityMagic;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class GravityEyePositionMixin {
    @Shadow
    public abstract double getX();

    @Shadow
    public abstract double getY();

    @Shadow
    public abstract double getZ();

    @Shadow
    public double lastX;

    @Shadow
    public double lastY;

    @Shadow
    public double lastZ;

    @Shadow
    public abstract float getHeight();

    @Shadow
    public abstract float getStandingEyeHeight();

    @Shadow
    public abstract float getEyeHeight(EntityPose pose);

    @Shadow
    public abstract EntityPose getPose();

    @Inject(method = "getEyeY", at = @At("HEAD"), cancellable = true)
    private void monvhua$getInvertedEyeY(CallbackInfoReturnable<Double> cir) {
        Entity entity = (Entity) (Object) this;
        if (isLocalInvertedPlayer(entity)) {
            cir.setReturnValue(invertedEyeY(this.getY()));
        }
    }

    @Inject(method = "getCameraPosVec", at = @At("HEAD"), cancellable = true)
    private void monvhua$getInvertedCameraPosVec(float tickProgress, CallbackInfoReturnable<Vec3d> cir) {
        Entity entity = (Entity) (Object) this;
        if (!isLocalInvertedPlayer(entity)) {
            return;
        }

        double x = MathHelper.lerp(tickProgress, this.lastX, this.getX());
        double y = MathHelper.lerp(tickProgress, this.lastY, this.getY());
        double z = MathHelper.lerp(tickProgress, this.lastZ, this.getZ());
        cir.setReturnValue(new Vec3d(x, invertedEyeY(y), z));
    }

    private double invertedEyeY(double baseY) {
        return baseY + this.getHeight() - this.getEyeHeight(this.getPose());
    }

    private static boolean isLocalInvertedPlayer(Entity entity) {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player == entity && GravityMagic.isInInvertedArea(entity);
    }
}
