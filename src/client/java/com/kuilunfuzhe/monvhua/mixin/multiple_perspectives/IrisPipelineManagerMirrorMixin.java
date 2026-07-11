package com.kuilunfuzhe.monvhua.mixin.multiple_perspectives;

import com.kuilunfuzhe.monvhua.features.mirror.FramebufferOverride;
import com.kuilunfuzhe.monvhua.features.portal.client.PortalFramebufferOverride;
import net.irisshaders.iris.pipeline.PipelineManager;
import net.irisshaders.iris.pipeline.VanillaRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(value = PipelineManager.class, remap = false)
public class IrisPipelineManagerMirrorMixin {
	private static final WorldRenderingPipeline MONVHUA_MIRROR_PIPELINE = new VanillaRenderingPipeline();

	@Shadow
	private WorldRenderingPipeline pipeline;

	@Inject(method = "preparePipeline", at = @At("HEAD"), cancellable = true)
	private void monvhua$useVanillaPipelineForMirror(NamespacedId dimension, CallbackInfoReturnable<WorldRenderingPipeline> cir) {
		if (isAuxiliaryRender()) {
			this.pipeline = MONVHUA_MIRROR_PIPELINE;
			cir.setReturnValue(MONVHUA_MIRROR_PIPELINE);
		}
	}

	@Inject(method = "getPipelineNullable", at = @At("HEAD"), cancellable = true)
	private void monvhua$getVanillaPipelineForMirror(CallbackInfoReturnable<WorldRenderingPipeline> cir) {
		if (isAuxiliaryRender()) {
			cir.setReturnValue(MONVHUA_MIRROR_PIPELINE);
		}
	}

	@Inject(method = "getPipeline", at = @At("HEAD"), cancellable = true)
	private void monvhua$getOptionalVanillaPipelineForMirror(CallbackInfoReturnable<Optional<WorldRenderingPipeline>> cir) {
		if (isAuxiliaryRender()) {
			cir.setReturnValue(Optional.of(MONVHUA_MIRROR_PIPELINE));
		}
	}

    private static boolean isAuxiliaryRender() {
        return FramebufferOverride.getOverride() != null || PortalFramebufferOverride.get() != null;
    }
}
