package com.kuilunfuzhe.monvhua.features.gazeguidance;

import com.kuilunfuzhe.monvhua.network.SafeClientNetworking;
import com.kuilunfuzhe.monvhua.network.gazeguidance.RightClickActionPacket;
import com.kuilunfuzhe.monvhua.item.gazeguidance.ModItems;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * 诱导法杖客户端。
 * 管理右键长按检测（发送网络包）、能量HUD渲染、阶段提示Toast，
 * 并提供射线目标实体获取工具方法。
 */
public class GazeguidanceClient {
	private static KeyBinding stageConfigKey;
	/** 上一tick右键状态，用于上升沿检测 */
	private static boolean lastRightClickState = false;
	/** 当前诱导法杖阶段 */
	private static int currentStrength = 0;
	/** 当前能量值 */
	private static double currentEnergy = 0;
	/** 最大能量值（100） */
	private static double maxEnergy = 100;
	/** 当前标记数量 */
	private static int currentMarkCount = 0;
	/** 最大可标记数量 */
	private static int currentMaxMarks = 0;
	private static List<String> currentMarkedNames = List.of();

	/**
	 * 设置能量值（由网络接收器调用）。
	 * @param current 当前能量
	 * @param max     最大能量
	 */
	public static void setEnergy(double current, double max) {
		currentEnergy = current;
		maxEnergy = max;
	}

	/**
	 * 设置标记数量（由网络接收器调用）。
	 * @param count 当前标记数
	 */
	public static void setMarkCount(int count) {
		currentMarkCount = count;
		if (count == 0) {
			currentMarkedNames = List.of();
		}
	}

	public static void setMarkedNames(List<String> names) {
		currentMarkedNames = names == null ? List.of() : new ArrayList<>(names);
		currentMarkCount = currentMarkedNames.size();
	}

	/**
	 * 设置诱导法杖阶段并在阶段变化时显示Toast提示。
	 * @param stage    新阶段编号
	 * @param maxMarks 最大标记数
	 */
	public static void setStrength(int stage, int maxMarks) {
		currentMaxMarks = maxMarks;
		if (stage != currentStrength) {
			currentStrength = stage;
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player != null && client.player.getMainHandStack().getItem() == ModItems.MAGIC_STICK) {
				showStageToast(stage, currentMarkCount, maxMarks);
			}
		}
	}

	private static void showStageToast(int stage, int markCount, int maxMarks) {
		MinecraftClient client = MinecraftClient.getInstance();
		SystemToast.show(client.getToastManager(), SystemToast.Type.NARRATOR_TOGGLE,
				Text.literal("§6诱导"),
				Text.literal("阶段 §a" + stage)
		);
	}

	/**
	 * 初始化诱导法杖客户端。
	 * 注册tick事件处理（右键长按检测）和HUD能量条渲染。
	 * 网络包接收器已移至ClairvoyanceClient统一管理。
	 */
	public static void initialize() {
		// 所有接收器已移至 ClairvoyanceClient，此处不再注册
		// 只保留业务逻辑所需的初始化（如按键绑定）
//		stageConfigKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
//				"key.gazeguidance.stage_config", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_U, "category.gazeguidance"));

		// Tick 事件处理（右键长按检测）
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null) return;
			// 右键长按检测
			boolean rightPressed = client.options.useKey.isPressed();
			if (rightPressed != lastRightClickState) {
				lastRightClickState = rightPressed;
				ItemStack stack = client.player.getMainHandStack();
				if (stack.getItem() == ModItems.MAGIC_STICK) {
					SafeClientNetworking.send(new RightClickActionPacket(rightPressed));
				}
			}
			// K 键打开配置界面（仅创造模式）
//			if (stageConfigKey.wasPressed() && client.player.isCreative()) {
//				// 注意：StageConfigScreen 可能需要从旧包导入，但此处暂时注释
//				// client.setScreen(new StageConfigScreen(null));
//				client.player.sendMessage(Text.literal("§a阶段配置界面待实现"), true);
//			} else if (stageConfigKey.wasPressed()) {
//				client.player.sendMessage(Text.literal("§c仅创造模式可配置"), true);
//			}
		});

		// HUD 渲染（能量条）
		net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player == null) return;
			ItemStack mainHand = client.player.getMainHandStack();
			if (mainHand.getItem() != ModItems.MAGIC_STICK) return;

			int screenWidth = drawContext.getScaledWindowWidth();
			int screenHeight = drawContext.getScaledWindowHeight();
			int barWidth = 80;  // 能量条宽度
			int barHeight = 5;  // 能量条高度
			int x = (screenWidth - barWidth) / 2; // 屏幕水平居中
			int y = screenHeight - 49; // 能量条Y坐标，紧贴经验条上方

			drawContext.fill(x, y, x + barWidth, y + barHeight, 0xFF444444); // 能量条背景（深灰）
			int fillWidth = (int)(barWidth * (currentEnergy / maxEnergy));
			drawContext.fill(x, y, x + fillWidth, y + barHeight, 0xFF55AAFF); // 能量条前景（蓝色）

			String text = String.format("%.1f/%.0f", currentEnergy, maxEnergy);
			int textX = x + barWidth + 5; // 文字在能量条右侧5像素
			int textY = y - 2; // 文字在能量条上方2像素
			drawContext.drawText(client.textRenderer, text, textX, textY, 0xFF5555, true);
			renderMarkedList(drawContext, client);
		});

//		System.out.println("GazeguidanceClient: Initialized (receivers moved to ClairvoyanceClient)")
//		;
	}

	private static void renderMarkedList(net.minecraft.client.gui.DrawContext drawContext, MinecraftClient client) {
		int x = 10;
		int y = Math.max(36, drawContext.getScaledWindowHeight() / 2 - 58);
		int width = 116;
		int rowHeight = 11;
		int visibleRows = Math.max(1, Math.min(currentMarkedNames.size(), 8));
		int rows = currentMarkedNames.isEmpty() ? 1 : visibleRows;
		int height = 22 + rows * rowHeight + 5;

		drawContext.fill(x, y, x + width, y + height, 0x8809121D);
		drawContext.fill(x, y, x + width, y + 1, 0xFF66BFFF);
		drawContext.fill(x, y + height - 1, x + width, y + height, 0xFF66BFFF);
		drawContext.fill(x, y, x + 1, y + height, 0xFF66BFFF);
		drawContext.fill(x + width - 1, y, x + width, y + height, 0xFF66BFFF);

		String title = "诱导标记 " + currentMarkCount + "/" + currentMaxMarks;
		drawContext.drawText(client.textRenderer, title, x + 6, y + 6, 0xFFFFFF, true);
		if (currentMarkedNames.isEmpty()) {
			drawContext.drawText(client.textRenderer, "无标记", x + 8, y + 20, 0xFFAAAAAA, true);
			return;
		}
		for (int i = 0; i < visibleRows; i++) {
			String name = currentMarkedNames.get(i);
			if (client.textRenderer.getWidth(name) > width - 18) {
				name = client.textRenderer.trimToWidth(name, width - 24) + "...";
			}
			drawContext.drawText(client.textRenderer, (i + 1) + ". " + name, x + 8, y + 20 + i * rowHeight, 0xFFEAF6FF, true);
		}
	}

		/**
		 * 射线检测获取玩家视线方向最近的活体实体。
		 * 在maxRange范围内沿玩家视线做包围盒射线检测，返回最近的命中实体。
		 *
		 * @param client   Minecraft客户端实例
		 * @param maxRange 最大检测距离
		 * @return 最近的活体实体，未命中则返回null
		 */
		public static Entity getTargetEntity(MinecraftClient client, double maxRange) {
		if (client.player == null || client.world == null) return null;
		Vec3d start = client.player.getEyePos();
		Vec3d direction = client.player.getRotationVec(1.0f);
		Vec3d end = start.add(direction.multiply(maxRange));
		Box searchBox = client.player.getBoundingBox().stretch(direction.multiply(maxRange)).expand(1.0);
		Entity closest = null;
		double closestDistSq = maxRange * maxRange;
		for (Entity entity : client.world.getOtherEntities(client.player, searchBox,
				e -> e instanceof LivingEntity && e.isAlive())) {
			Vec3d hitPos = entity.getBoundingBox().raycast(start, end).orElse(null);
			if (hitPos != null) {
				double distSq = start.squaredDistanceTo(hitPos);
				if (distSq < closestDistSq) {
					closestDistSq = distSq;
					closest = entity;
				}
			}
		}
		return closest;
	}
}
