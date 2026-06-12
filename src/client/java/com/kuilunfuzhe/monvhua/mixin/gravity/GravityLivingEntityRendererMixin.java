package com.kuilunfuzhe.monvhua.mixin.gravity;

import com.kuilunfuzhe.monvhua.features.gravity.GravityClient;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class GravityLivingEntityRendererMixin {
    @Inject(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V", at = @At("RETURN"))
    private void monvhua$flipInvertedAreaEntity(LivingEntity entity, LivingEntityRenderState state, float tickDelta, CallbackInfo ci) {
        if (GravityClient.isEntityInInvertedField(entity)) {
            state.flipUpsideDown = true;
            state.pitch = -state.pitch;
            state.relativeHeadYaw = -state.relativeHeadYaw;
        }
    }
}
