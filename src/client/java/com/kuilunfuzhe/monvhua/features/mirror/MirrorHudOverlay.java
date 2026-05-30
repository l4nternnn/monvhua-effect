package com.kuilunfuzhe.monvhua.features.mirror;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;

/**
 * 镜像HUD叠加层。
 * 在HUD渲染阶段将对角线分割的镜像画面复制到主帧缓冲上，
 * 实现镜像视角与正常画面的渐变融合效果。
 */
public class MirrorHudOverlay {
	/** 注册HUD渲染回调，将镜像对角线分割画面覆盖到屏幕上 */
	public static void register() {
		HudRenderCallback.EVENT.register((context, tickCounter) -> {
			if (!MirrorClientManager.isActive()) return;
			if (MinecraftClient.getInstance().player == null) return;

			MirrorViewportRenderer.renderDiagonalSplit(context);
		});
	}
}
