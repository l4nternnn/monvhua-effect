package com.kuilunfuzhe.monvhua.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 示例客户端Mixin：注入到MinecraftClient的run方法头部。
 * 作为客户端Mixin开发的模板参考，演示客户端侧的@Inject基本用法。
 */
@Mixin(MinecraftClient.class)
public class ExampleClientMixin {
	@Inject(at = @At("HEAD"), method = "run")
	private void init(CallbackInfo info) {
		// 此代码注入到 MinecraftClient.run() 的开头
	}
}