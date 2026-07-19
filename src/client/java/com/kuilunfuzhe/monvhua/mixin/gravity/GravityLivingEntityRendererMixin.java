package com.kuilunfuzhe.monvhua.mixin.gravity;

import com.google.common.collect.ImmutableList;
import com.kuilunfuzhe.monvhua.features.gravity.GravityClient;
import com.kuilunfuzhe.monvhua.features.gravity.GravityMagic;
import com.kuilunfuzhe.monvhua.features.gravity.SurfaceGravityClientEngine;
import com.kuilunfuzhe.monvhua.features.gravity.SurfaceGravityCollision;
import com.kuilunfuzhe.monvhua.features.gravity.SurfaceGravityEngine;
import net.minecraft.client.render.entity.state.EntityHitbox;
import net.minecraft.client.render.entity.state.EntityHitboxAndView;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class GravityLivingEntityRendererMixin {
    @Unique
    private boolean monvhua$surfaceGravityTransformActive;

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V", at = @At("RETURN"))
    private void monvhua$flipInvertedAreaEntity(LivingEntity entity, LivingEntityRenderState state, float tickDelta, CallbackInfo ci) {
        if (GravityClient.isEntityInInvertedField(entity)) {
            state.flipUpsideDown = true;
            state.pitch = -state.pitch;
            state.relativeHeadYaw = -state.relativeHeadYaw;
        }
        monvhua$updateSurfaceGravityHeadAngles(entity, state, tickDelta);
        monvhua$updateSurfaceGravityHitbox(entity, state, tickDelta);
    }

    @Inject(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;setupTransforms(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;FF)V", shift = At.Shift.BEFORE),
            require = 0)
    private void monvhua$applySurfaceGravityModelTransform(LivingEntityRenderState state, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        if (!(state instanceof PlayerEntityRenderState playerState)) {
            return;
        }
        Entity entity = monvhua$getRenderedEntity(playerState.id);
        if (!SurfaceGravityClientEngine.isRenderActive(entity)) {
            return;
        }
        matrices.push();
        matrices.multiply(SurfaceGravityClientEngine.modelRotation(entity));
        monvhua$surfaceGravityTransformActive = true;
    }

    @Inject(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;pop()V"),
            require = 0)
    private void monvhua$restoreSurfaceGravityModelTransform(LivingEntityRenderState state, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        if (!monvhua$surfaceGravityTransformActive) {
            return;
        }
        matrices.pop();
        monvhua$surfaceGravityTransformActive = false;
    }

    @Unique
    private static Entity monvhua$getRenderedEntity(int entityId) {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.world == null ? null : client.world.getEntityById(entityId);
    }

    @Unique
    private static void monvhua$updateSurfaceGravityHitbox(Entity entity, LivingEntityRenderState state, float tickDelta) {
        Direction downDirection = GravityMagic.getSurfaceGravityDirection(entity);
        if (downDirection == null || downDirection == Direction.DOWN || state.hitbox == null) {
            return;
        }
        Vec3d anchor = SurfaceGravityCollision.anchorFromBox(downDirection, entity.getBoundingBox());
        Vec3d origin = new Vec3d(state.x, state.y, state.z);
        Box box = entity.getBoundingBox().offset(origin.subtract(anchor));
        Vec3d look = GravityMagic.getSurfaceLook(entity);
        EntityHitbox hitbox = new EntityHitbox(
                box.minX - state.x,
                box.minY - state.y,
                box.minZ - state.z,
                box.maxX - state.x,
                box.maxY - state.y,
                box.maxZ - state.z,
                1.0F,
                1.0F,
                1.0F
        );
        state.hitbox = new EntityHitboxAndView(look.x, look.y, look.z, ImmutableList.of(hitbox));
    }

    @Unique
    private static void monvhua$updateSurfaceGravityHeadAngles(
            Entity entity,
            LivingEntityRenderState state,
            float tickDelta
    ) {
        Direction downDirection = GravityMagic.getSurfaceGravityDirection(entity);
        SurfaceGravityEngine.SurfaceState surfaceState = GravityMagic.getSurfaceState(entity);
        if (downDirection == null || downDirection == Direction.DOWN || surfaceState == null) {
            return;
        }

        float bodyYaw = MathHelper.lerpAngleDegrees(
                tickDelta,
                surfaceState.lastLocalBodyYaw(),
                surfaceState.localBodyYaw()
        );
        state.bodyYaw = bodyYaw + monvhua$surfaceRenderYawOffset(downDirection);
        state.relativeHeadYaw = MathHelper.wrapDegrees(surfaceState.localYaw() - bodyYaw);
        state.pitch = surfaceState.localPitch();
        if (state instanceof BipedEntityRenderState bipedState) {
            bipedState.leaningPitch = 0.0F;
        }
        if (state instanceof PlayerEntityRenderState playerState) {
            playerState.applyFlyingRotation = false;
            playerState.flyingRotation = 0.0F;
        }
    }

    @Unique
    private static float monvhua$surfaceRenderYawOffset(Direction downDirection) {
        return 0.0F;
    }
}
