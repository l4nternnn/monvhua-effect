package com.kuilunfuzhe.monvhua.mixin;
import com.kuilunfuzhe.monvhua.features.forensics.viewport.*;
import net.minecraft.client.gui.render.*;
import net.minecraft.client.gui.render.state.special.SpecialGuiElementRenderState;
import net.minecraft.client.render.VertexConsumerProvider;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Map;

@Mixin(GuiRenderer.class)
public abstract class EvidenceGuiRendererMixin {
    @Shadow @Final @Mutable private Map<Class<? extends SpecialGuiElementRenderState>, SpecialGuiElementRenderer<?>> specialElementRenderers;
    @Shadow @Final private VertexConsumerProvider.Immediate vertexConsumers;
    @Inject(method="<init>",at=@At("TAIL"))
    private void evidence$register(CallbackInfo ci) {
        specialElementRenderers=new java.util.HashMap<>(specialElementRenderers);
        specialElementRenderers.put(EvidenceViewportRenderState.class,new EvidenceViewportRenderer(vertexConsumers));
    }
    @Inject(method="incrementFrame",at=@At("TAIL"))
    private void evidence$beginFrame(CallbackInfo ci) {
        if(specialElementRenderers.get(EvidenceViewportRenderState.class) instanceof EvidenceViewportRenderer renderer)renderer.beginFrame();
    }
}
