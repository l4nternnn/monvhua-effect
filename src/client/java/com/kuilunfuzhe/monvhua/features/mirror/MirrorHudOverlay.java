package com.kuilunfuzhe.monvhua.features.mirror;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;

public class MirrorHudOverlay {
	public static void register() {
		HudRenderCallback.EVENT.register((context, tickCounter) -> {
			if (!MirrorClientManager.isActive()) return;
			if (MinecraftClient.getInstance().player == null) return;

			MirrorViewportRenderer.renderDiagonalSplit(context);
		});
	}
}
