package com.kuilunfuzhe.monvhua.mixin.gravity;

import com.kuilunfuzhe.monvhua.features.gravity.GravityMagic;
import com.kuilunfuzhe.monvhua.features.gravity.SurfaceGravityCollision;
import com.kuilunfuzhe.monvhua.features.gravity.SurfaceGravityEngine;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerPosition;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

@Mixin(Entity.class)
public abstract class SurfaceGravityBoundingBoxMixin {
    @Unique
    private boolean monvhua$refreshingSurfaceGravityBox;
    @Unique
    private Direction monvhua$dimensionsSurfaceDirection;
    @Unique
    private Vec3d monvhua$dimensionsSurfaceAnchor;

    @Inject(method = "calculateDefaultBoundingBox", at = @At("HEAD"), cancellable = true)
    private void monvhua$calculateSurfaceGravityBoundingBox(Vec3d pos, CallbackInfoReturnable<Box> cir) {
        Entity entity = (Entity) (Object) this;
        Direction downDirection = GravityMagic.getSurfaceGravityDirection(entity);
        if (downDirection == null || downDirection == Direction.DOWN) {
            return;
        }
        cir.setReturnValue(SurfaceGravityCollision.boxAt(entity, downDirection, pos));
    }

    @Inject(method = "setPosition(DDD)V", at = @At("RETURN"))
    private void monvhua$refreshSurfaceGravityBoxAfterPosition(double x, double y, double z, CallbackInfo ci) {
        monvhua$refreshSurfaceGravityBox();
    }

    @Inject(method = "setPosition(Lnet/minecraft/entity/player/PlayerPosition;Ljava/util/Set;)V", at = @At("RETURN"))
    private void monvhua$refreshSurfaceGravityBoxAfterPlayerPosition(PlayerPosition position, Set<PositionFlag> flags, CallbackInfo ci) {
        monvhua$refreshSurfaceGravityBox();
    }

    @Inject(method = "refreshPosition", at = @At("RETURN"))
    private void monvhua$refreshSurfaceGravityBoxAfterRefreshPosition(CallbackInfo ci) {
        monvhua$refreshSurfaceGravityBox();
    }

    @Inject(method = "calculateDimensions", at = @At("HEAD"))
    private void monvhua$captureSurfaceGravityAnchorBeforeDimensions(CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        Direction downDirection = GravityMagic.getSurfaceGravityDirection(entity);
        if (downDirection == null || downDirection == Direction.DOWN) {
            monvhua$dimensionsSurfaceDirection = null;
            monvhua$dimensionsSurfaceAnchor = null;
            return;
        }
        monvhua$dimensionsSurfaceDirection = downDirection;
        monvhua$dimensionsSurfaceAnchor = SurfaceGravityCollision.anchorFromBox(downDirection, entity.getBoundingBox());
    }

    @Inject(method = "calculateDimensions", at = @At("RETURN"))
    private void monvhua$refreshSurfaceGravityBoxAfterDimensions(CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        Direction downDirection = GravityMagic.getSurfaceGravityDirection(entity);
        if (downDirection == null || downDirection == Direction.DOWN) {
            monvhua$dimensionsSurfaceDirection = null;
            monvhua$dimensionsSurfaceAnchor = null;
            return;
        }
        Vec3d anchor = downDirection == monvhua$dimensionsSurfaceDirection
                ? monvhua$dimensionsSurfaceAnchor
                : null;
        monvhua$dimensionsSurfaceDirection = null;
        monvhua$dimensionsSurfaceAnchor = null;
        if (anchor != null) {
            SurfaceGravityCollision.setAnchorAndRefreshBox(entity, downDirection, anchor);
        } else {
            monvhua$refreshSurfaceGravityBox();
        }
    }

    @Unique
    private void monvhua$refreshSurfaceGravityBox() {
        if (monvhua$refreshingSurfaceGravityBox) {
            return;
        }
        Entity entity = (Entity) (Object) this;
        Direction downDirection = GravityMagic.getSurfaceGravityDirection(entity);
        if (downDirection == null || downDirection == Direction.DOWN) {
            return;
        }
        monvhua$refreshingSurfaceGravityBox = true;
        SurfaceGravityCollision.refreshBox(entity, downDirection);
        monvhua$refreshingSurfaceGravityBox = false;
    }

    @Inject(method = "getEyePos", at = @At("HEAD"), cancellable = true)
    private void monvhua$getSurfaceGravityEyePos(CallbackInfoReturnable<Vec3d> cir) {
        Entity entity = (Entity) (Object) this;
        Direction downDirection = GravityMagic.getSurfaceGravityDirection(entity);
        if (downDirection == null || downDirection == Direction.DOWN) {
            return;
        }
        cir.setReturnValue(GravityMagic.getSurfaceEyePos(entity, 1.0F));
    }

    @Inject(method = "getRotationVec", at = @At("HEAD"), cancellable = true)
    private void monvhua$getSurfaceGravityRotationVec(float tickProgress, CallbackInfoReturnable<Vec3d> cir) {
        Entity entity = (Entity) (Object) this;
        if (!GravityMagic.hasNonNormalSurfaceGravity(entity)) {
            return;
        }
        cir.setReturnValue(GravityMagic.getSurfaceLook(entity));
    }

    @Inject(method = "getLandingPos", at = @At("HEAD"), cancellable = true)
    private void monvhua$getSurfaceGravityLandingPos(CallbackInfoReturnable<BlockPos> cir) {
        Entity entity = (Entity) (Object) this;
        Direction downDirection = GravityMagic.getSurfaceGravityDirection(entity);
        if (downDirection == null || downDirection == Direction.DOWN) {
            return;
        }
        BlockPos pos = SurfaceGravityEngine.getSurfaceLandingPos(entity, downDirection, 0.2F);
        if (pos != null) {
            cir.setReturnValue(pos);
        }
    }

    @Inject(method = "getSteppingPos", at = @At("HEAD"), cancellable = true)
    private void monvhua$getSurfaceGravitySteppingPos(CallbackInfoReturnable<BlockPos> cir) {
        Entity entity = (Entity) (Object) this;
        Direction downDirection = GravityMagic.getSurfaceGravityDirection(entity);
        if (downDirection == null || downDirection == Direction.DOWN) {
            return;
        }
        BlockPos pos = SurfaceGravityEngine.getSurfaceLandingPos(entity, downDirection, 1.0E-5F);
        if (pos != null) {
            cir.setReturnValue(pos);
        }
    }
}
