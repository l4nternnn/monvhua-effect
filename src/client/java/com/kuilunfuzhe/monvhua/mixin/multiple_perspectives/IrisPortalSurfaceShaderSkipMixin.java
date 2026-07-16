package com.kuilunfuzhe.monvhua.mixin.multiple_perspectives;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gl.ShaderProgram;
import net.irisshaders.iris.gl.blending.DepthColorStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = ShaderProgram.class, priority = 900)
public abstract class IrisPortalSurfaceShaderSkipMixin {
    private static final String PORTAL_SURFACE_PIPELINE = "monvhua:pipeline/portal_surface";

    @Inject(method = "set", at = @At("TAIL"))
    private void monvhua$keepPortalSurfaceNativeShaderWritable(List<RenderPipeline.UniformDescription> uniforms,
                                                               List<String> samplers,
                                                               CallbackInfo ci) {
        ShaderProgram program = (ShaderProgram) (Object) this;
        if (PORTAL_SURFACE_PIPELINE.equals(program.getDebugLabel())) {
            DepthColorStorage.unlockDepthColor();
        }
    }
}
