package com.kuilunfuzhe.monvhua.mixin.gravity;

import com.kuilunfuzhe.monvhua.features.gravity.GravityMagic;
import com.kuilunfuzhe.monvhua.features.gravity.SurfaceGravityClientEngine;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
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
    protected abstract float clipToSpace(float desiredCameraDistance);

    @Shadow
    private Vec3d pos;

    @Shadow
    private float lastCameraY;

    @Shadow
    private float cameraY;

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
        if (client.player != null && focusedEntity == client.player && SurfaceGravityClientEngine.isActive(focusedEntity)) {
            SurfaceGravityClientEngine.applyCamera(focusedEntity, this.rotation, this.horizontalPlane, this.verticalPlane, this.diagonalPlane, inverseView);
            Vec3d surfaceEye = SurfaceGravityClientEngine.eyePos(focusedEntity, tickProgress);
            if (!thirdPerson) {
                this.setPos(surfaceEye.x, surfaceEye.y, surfaceEye.z);
                return;
            }

            this.setPos(surfaceEye.x, surfaceEye.y, surfaceEye.z);
            float cameraDistance = this.clipToSpace(4.0F);
            Vec3d cameraBack = SurfaceGravityClientEngine.look(focusedEntity).multiply(-cameraDistance);
            if (inverseView) {
                cameraBack = cameraBack.multiply(-1.0D);
            }
            this.setPos(surfaceEye.x + cameraBack.x, surfaceEye.y + cameraBack.y, surfaceEye.z + cameraBack.z);
            return;
        }

        if (client.player == null || focusedEntity != client.player || !GravityMagic.isInInvertedArea(focusedEntity)) {
            return;
        }

        this.rotation.rotateZ((float) Math.PI);
        this.verticalPlane.negate();
        this.diagonalPlane.negate();

        Vec3d lerpedPos = focusedEntity.getLerpedPos(tickProgress);
        double invertedEyeY = lerpedPos.y + focusedEntity.getHeight() - focusedEntity.getEyeHeight(focusedEntity.getPose());
        if (!thirdPerson) {
            this.setPos(lerpedPos.x, invertedEyeY, lerpedPos.z);
            return;
        }

        double vanillaEyeY = MathHelper.lerp(tickProgress, this.lastCameraY, this.cameraY) + lerpedPos.y;
        double deltaY = invertedEyeY - vanillaEyeY;
        this.setPos(this.pos.x, this.pos.y + deltaY, this.pos.z);
    }
}
