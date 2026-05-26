package com.kuilunfuzhe.monvhua.features.mirror;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class MirrorHudOverlay {
	private static final int VIEWPORT_WIDTH = 320;
	private static final int VIEWPORT_HEIGHT = 180;
	private static final int PADDING = 8;

	public static void register() {
		HudRenderCallback.EVENT.register((context, tickCounter) -> {
			if (!MirrorClientManager.isActive()) return;

			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player == null) return;

			// 将镜像帧缓冲渲染到主帧缓冲
			MirrorViewportRenderer.blitToMainFramebuffer();

			// 绘制边框和标签
			int scaleFactor = (int) client.getWindow().getScaleFactor();
			int vpW = Math.ceilDiv(VIEWPORT_WIDTH, scaleFactor);
			int vpH = Math.ceilDiv(VIEWPORT_HEIGHT, scaleFactor);
			int sw = client.getWindow().getScaledWidth();

			int y = PADDING;
			for (int slot = 0; slot < 2; slot++) {
				MirrorClientManager.CameraData data = MirrorClientManager.getSlot(slot);
				if (!data.active()) continue;

				int x = sw - vpW - PADDING;

				// 边框
				int borderColor = slot == 0 ? 0xFFFF55FF : 0xFF55FFFF;
				context.fill(x - 1, y - 1, x + vpW + 1, y + vpH + 1, borderColor);

				// 标签背景条
				context.fill(x, y, x + 40, y + 12, 0xAA000000);

				// 标签
				String label = slot == 0 ? "镜1" : "镜2";
				int labelColor = slot == 0 ? 0xFFFF55FF : 0xFF55FFFF;
				context.drawText(client.textRenderer, Text.literal(label),
					x + 4, y + 2, labelColor, false);

				y += vpH + PADDING;
			}
		});
	}
}
