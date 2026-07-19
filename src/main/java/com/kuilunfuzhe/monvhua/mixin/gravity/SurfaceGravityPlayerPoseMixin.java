package com.kuilunfuzhe.monvhua.mixin.gravity;

import com.kuilunfuzhe.monvhua.features.gravity.GravityMagic;
import com.kuilunfuzhe.monvhua.features.gravity.SurfaceGravityCollision;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class SurfaceGravityPlayerPoseMixin {
    @Inject(method = "canChangeIntoPose", at = @At("HEAD"), cancellable = true)
    private void monvhua$canChangeIntoSurfacePose(EntityPose pose, CallbackInfoReturnable<Boolean> cir) {
        Entity entity = (Entity) (Object) this;
        Direction downDirection = GravityMagic.getSurfaceGravityDirection(entity);
        if (downDirection == null || downDirection == Direction.DOWN) {
            return;
        }
        if ((pose == EntityPose.CROUCHING || pose == EntityPose.SWIMMING)
                && entity.isSneaking()) {
            cir.setReturnValue(true);
            return;
        }
        Vec3d anchor = SurfaceGravityCollision.anchorFromBox(downDirection, entity.getBoundingBox());
        cir.setReturnValue(entity.getWorld().isSpaceEmpty(
                entity,
                SurfaceGravityCollision.boxAt(entity, downDirection, anchor, pose).contract(1.0E-7D)
        ));
    }
}
