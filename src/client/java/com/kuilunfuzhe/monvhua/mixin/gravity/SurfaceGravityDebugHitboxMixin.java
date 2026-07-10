package com.kuilunfuzhe.monvhua.mixin.gravity;

import com.kuilunfuzhe.monvhua.features.gravity.GravityMagic;
import com.kuilunfuzhe.monvhua.features.gravity.SurfaceGravityCollision;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.state.EntityHitboxAndView;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public abstract class SurfaceGravityDebugHitboxMixin {
    @Inject(
            method = "renderHitboxes(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/entity/state/EntityRenderState;Lnet/minecraft/client/render/entity/state/EntityHitboxAndView;Lnet/minecraft/client/render/VertexConsumerProvider;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void monvhua$renderSurfaceGravityHitboxes(MatrixStack matrices, EntityRenderState state, EntityHitboxAndView hitbox, VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
        Entity entity = monvhua$getEntity(state);
        Direction downDirection = GravityMagic.getSurfaceGravityDirection(entity);
        if (entity == null || downDirection == null) {
            return;
        }

        Vec3d anchor = SurfaceGravityCollision.anchorFromBox(downDirection, entity.getBoundingBox());
        Vec3d renderOrigin = new Vec3d(state.x, state.y, state.z);
        Box box = entity.getBoundingBox().offset(renderOrigin.subtract(anchor));
        Vec3d eye = SurfaceGravityCollision.eyePosFromBox(entity, downDirection, box);
        Vec3d eyeLocal = eye.subtract(renderOrigin);
        Vec3d look = GravityMagic.getSurfaceLook(entity);

        VertexConsumer vertices = vertexConsumers.getBuffer(RenderLayer.getLines());
        VertexRendering.drawBox(
                matrices,
                vertices,
                box.minX - state.x,
                box.minY - state.y,
                box.minZ - state.z,
                box.maxX - state.x,
                box.maxY - state.y,
                box.maxZ - state.z,
                1.0F,
                1.0F,
                1.0F,
                1.0F
        );
        VertexRendering.drawVector(
                matrices,
                vertices,
                new Vector3f((float) eyeLocal.x, (float) eyeLocal.y, (float) eyeLocal.z),
                look.multiply(3.0D),
                -16776961
        );
        ci.cancel();
    }

    @Unique
    private static Entity monvhua$getEntity(EntityRenderState state) {
        if (!(state instanceof PlayerEntityRenderState playerState)) {
            return null;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        return client.world == null ? null : client.world.getEntityById(playerState.id);
    }
}
