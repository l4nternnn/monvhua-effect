package com.kuilunfuzhe.monvhua.mixin.gravity;

import com.kuilunfuzhe.monvhua.features.gravity.GravityMagic;
import com.kuilunfuzhe.monvhua.features.gravity.SurfaceGravityCollision;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerPosition;
import net.minecraft.network.packet.s2c.play.PositionFlag;
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

    @Inject(method = "calculateDefaultBoundingBox", at = @At("HEAD"), cancellable = true)
    private void monvhua$calculateSurfaceGravityBoundingBox(Vec3d pos, CallbackInfoReturnable<Box> cir) {
        Entity entity = (Entity) (Object) this;
        Direction downDirection = GravityMagic.getSurfaceGravityDirection(entity);
        if (downDirection == null) {
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

    @Inject(method = "calculateDimensions", at = @At("RETURN"))
    private void monvhua$refreshSurfaceGravityBoxAfterDimensions(CallbackInfo ci) {
        monvhua$refreshSurfaceGravityBox();
    }

    @Unique
    private void monvhua$refreshSurfaceGravityBox() {
        if (monvhua$refreshingSurfaceGravityBox) {
            return;
        }
        Entity entity = (Entity) (Object) this;
        Direction downDirection = GravityMagic.getSurfaceGravityDirection(entity);
        if (downDirection == null) {
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
        if (downDirection == null) {
            return;
        }
        cir.setReturnValue(SurfaceGravityCollision.eyePosFromBox(entity, downDirection,
                entity.getBoundingBox()));
    }

    @Inject(method = "getRotationVec", at = @At("HEAD"), cancellable = true)
    private void monvhua$getSurfaceGravityRotationVec(float tickProgress, CallbackInfoReturnable<Vec3d> cir) {
        Entity entity = (Entity) (Object) this;
        if (GravityMagic.getSurfaceGravityDirection(entity) == null) {
            return;
        }
        cir.setReturnValue(GravityMagic.getSurfaceLook(entity));
    }
}
