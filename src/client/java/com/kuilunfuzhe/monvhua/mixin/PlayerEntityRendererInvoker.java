package com.kuilunfuzhe.monvhua.mixin;

import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PlayerEntityRenderer.class)
public interface PlayerEntityRendererInvoker {
    @Invoker("setupTransforms")
    void monvhua$setupTransforms(PlayerEntityRenderState state, MatrixStack matrices, float bodyYaw, float baseHeight);

    @Invoker("scale")
    void monvhua$scale(PlayerEntityRenderState state, MatrixStack matrices);
}
