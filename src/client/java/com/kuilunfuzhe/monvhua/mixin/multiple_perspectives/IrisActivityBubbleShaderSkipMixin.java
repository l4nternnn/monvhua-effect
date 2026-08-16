package com.kuilunfuzhe.monvhua.mixin.multiple_perspectives;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.irisshaders.iris.gl.blending.DepthColorStorage;
import net.minecraft.client.gl.ShaderProgram;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = ShaderProgram.class, priority = 900)
public abstract class IrisActivityBubbleShaderSkipMixin {
    private static final String ACTIVITY_BUBBLE_PIPELINE = "monvhua:pipeline/ui_activity_bubble";

    @Inject(method = "set", at = @At("TAIL"))
    private void monvhua$keepActivityBubbleNativeShaderWritable(List<RenderPipeline.UniformDescription> uniforms,
                                                                List<String> samplers,
                                                                CallbackInfo ci) {
        ShaderProgram program = (ShaderProgram) (Object) this;
        if (ACTIVITY_BUBBLE_PIPELINE.equals(program.getDebugLabel())) {
            DepthColorStorage.unlockDepthColor();
        }
    }
}
