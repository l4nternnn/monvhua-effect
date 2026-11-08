package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.physics_preview.LiveCorpseRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public abstract class LiveCorpseRenderMixin {
    @Inject(method="render(Lnet/minecraft/entity/Entity;DDDFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",at=@At("HEAD"),cancellable=true)
    private void monvhua$liveCorpse(Entity entity,double x,double y,double z,float tickProgress,MatrixStack matrices,VertexConsumerProvider consumers,int light,CallbackInfo ci) {
        if(LiveCorpseRenderer.hides(entity))ci.cancel();
    }
}
