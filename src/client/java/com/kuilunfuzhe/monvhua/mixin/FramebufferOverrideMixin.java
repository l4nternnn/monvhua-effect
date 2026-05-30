package com.kuilunfuzhe.monvhua.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import com.kuilunfuzhe.monvhua.features.mirror.FramebufferOverride;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 帧缓冲覆写Mixin，拦截MinecraftClient.getFramebuffer方法，允许替换当前渲染目标。
 * 当FramebufferOverride提供了覆盖帧缓冲时，返回该帧缓冲作为渲染目标而不是默认的主帧缓冲。
 * 主要用于镜面渲染功能，将场景渲染到自定义帧缓冲后再处理。
 */
@Mixin(MinecraftClient.class)
public class FramebufferOverrideMixin {
	/**
	 * 拦截getFramebuffer调用，检查是否有帧缓冲覆写。
	 * 当FramebufferOverride.getOverride()返回非null时，用该值替换原返回值，将渲染目标切换到自定义帧缓冲；
	 * 返回null时不干预，走原逻辑返回主帧缓冲。
	 * @param cir 带返回值的回调信息，用于设置替代返回值
	 */
	@Inject(method = "getFramebuffer", at = @At("HEAD"), cancellable = true)
	private void onGetFramebuffer(CallbackInfoReturnable<Framebuffer> cir) {
		Framebuffer override = FramebufferOverride.getOverride();
		if (override != null) {
			cir.setReturnValue(override);
		}
	}
}
