package com.kuilunfuzhe.monvhua.mixin.multiple_perspectives;

import com.kuilunfuzhe.monvhua.features.mirror.FramebufferOverride;
import com.kuilunfuzhe.monvhua.features.portal.client.PortalFramebufferOverride;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.BlockRenderLayerGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockRenderLayerGroup.class)
public class BlockRenderLayerGroupFramebufferMixin {
    @Inject(method = "getFramebuffer", at = @At("HEAD"), cancellable = true)
    private void monvhua$getMirrorFramebufferOverride(CallbackInfoReturnable<Framebuffer> cir) {
        Framebuffer override = PortalFramebufferOverride.get();
		if (override == null) {
			override = FramebufferOverride.getOverride();
		}
		if (override != null) {
			cir.setReturnValue(override);
		}
	}
}
