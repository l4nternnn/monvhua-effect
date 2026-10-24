package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.gui.body.bodypose.viewport.PoseViewportRenderState;
import com.kuilunfuzhe.monvhua.gui.body.bodypose.viewport.PoseViewportRenderer;
import com.kuilunfuzhe.monvhua.gui.body.bodypose.BodyPoseEditorFragment;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.SpecialGuiElementRenderer;
import net.minecraft.client.gui.render.state.special.SpecialGuiElementRenderState;
import net.minecraft.client.render.VertexConsumerProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Map;

@Mixin(GuiRenderer.class)
public abstract class BodyPoseGuiRendererMixin {
    @Shadow @Final @Mutable private Map<Class<? extends SpecialGuiElementRenderState>, SpecialGuiElementRenderer<?>> specialElementRenderers;
    @Shadow @Final private VertexConsumerProvider.Immediate vertexConsumers;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void monvhua$registerPoseViewport(CallbackInfo ci) {
        specialElementRenderers = new java.util.HashMap<>(specialElementRenderers);
        specialElementRenderers.put(PoseViewportRenderState.class, new PoseViewportRenderer(vertexConsumers));
    }

    @Inject(method = "incrementFrame", at = @At("TAIL"))
    private void monvhua$releaseClosedViewport(CallbackInfo ci) {
        if (BodyPoseEditorFragment.activeInstance != null) return;
        if (specialElementRenderers.get(PoseViewportRenderState.class) instanceof PoseViewportRenderer renderer
                && renderer.hasRendered()) {
            renderer.close();
            specialElementRenderers.put(PoseViewportRenderState.class, new PoseViewportRenderer(vertexConsumers));
        }
    }
}
