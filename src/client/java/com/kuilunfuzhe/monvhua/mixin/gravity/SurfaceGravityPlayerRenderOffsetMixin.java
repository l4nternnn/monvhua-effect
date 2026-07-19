package com.kuilunfuzhe.monvhua.mixin.gravity;

import com.kuilunfuzhe.monvhua.features.gravity.GravityMagic;
import com.kuilunfuzhe.monvhua.features.gravity.SurfaceGravityBasis;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntityRenderer.class)
public abstract class SurfaceGravityPlayerRenderOffsetMixin {
    @Inject(method = "getPositionOffset(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)Lnet/minecraft/util/math/Vec3d;",
            at = @At("RETURN"), cancellable = true)
    private void monvhua$surfaceSneakRenderOffset(PlayerEntityRenderState state, CallbackInfoReturnable<Vec3d> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        Entity entity = client.world == null ? null : client.world.getEntityById(state.id);
        Direction downDirection = GravityMagic.getSurfaceGravityDirection(entity);
        if (downDirection == null || downDirection == Direction.DOWN || !state.isInSneakingPose) {
            return;
        }

        double vanillaSneakOffset = -2.0D * state.baseScale / 16.0D;
        Vec3d originalOffset = cir.getReturnValue();
        Vec3d withoutVanillaSneakOffset = originalOffset.subtract(0.0D, vanillaSneakOffset, 0.0D);
        Vec3d surfaceSneakOffset = SurfaceGravityBasis.directionVector(downDirection).multiply(-vanillaSneakOffset);
        cir.setReturnValue(withoutVanillaSneakOffset.add(surfaceSneakOffset));
    }
}
