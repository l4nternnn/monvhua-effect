package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.gravity.GravityMagic;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class GravityCameraMixin {
    @Shadow
    protected abstract void setPos(double x, double y, double z);

    @Shadow
    @Final
    private Quaternionf rotation;

    @Shadow
    @Final
    private Vector3f horizontalPlane;

    @Shadow
    @Final
    private Vector3f verticalPlane;

    @Shadow
    @Final
    private Vector3f diagonalPlane;

    @Inject(method = "update", at = @At("RETURN"))
    private void monvhua$rollCameraInInvertedArea(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickProgress, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || focusedEntity != client.player || !GravityMagic.isInInvertedArea(focusedEntity)) {
            return;
        }

        this.rotation.rotateZ((float) Math.PI);
        this.verticalPlane.negate();
        this.diagonalPlane.negate();

        if (!thirdPerson) {
            Vec3d pos = focusedEntity.getLerpedPos(tickProgress);
            double eyeY = pos.y + focusedEntity.getHeight() - focusedEntity.getStandingEyeHeight();
            this.setPos(pos.x, eyeY, pos.z);
        }
    }
}
