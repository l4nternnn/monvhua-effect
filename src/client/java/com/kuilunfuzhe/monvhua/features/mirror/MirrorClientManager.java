package com.kuilunfuzhe.monvhua.features.mirror;

import com.kuilunfuzhe.monvhua.network.mirror.MirrorStateS2CPacket;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

public class MirrorClientManager {
	private static boolean viewportActive = false;
	private static final CameraData[] slots = new CameraData[2];
	private static Vec3d origin = null; // 首次启用视口时的玩家位置

	static {
		slots[0] = new CameraData(false, Vec3d.ZERO);
		slots[1] = new CameraData(false, Vec3d.ZERO);
	}

	public record CameraData(boolean active, Vec3d pos) {}

	public static void onStatePacket(MirrorStateS2CPacket packet) {
		boolean wasActive = viewportActive;
		viewportActive = packet.viewportActive();

		// 首次启用时记录起点
		if (viewportActive && !wasActive && origin == null) {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player != null) {
				origin = client.player.getPos();
			}
		}

		slots[0] = new CameraData(packet.slot1Active(), packet.getPos1() != null ? packet.getPos1() : Vec3d.ZERO);
		slots[1] = new CameraData(packet.slot2Active(), packet.getPos2() != null ? packet.getPos2() : Vec3d.ZERO);
	}

	/**
	 * 镜像视口相机位于：保存的设置点 + 玩家相对于起点的位移。
	 * origin = 首次启用视口时玩家的位置。
	 * 当玩家移动时，视口跟随移动，保持相同的相对位置。
	 */
	public static Vec3d getOriginOffset() {
		if (origin == null) return Vec3d.ZERO;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) return Vec3d.ZERO;
		return client.player.getPos().subtract(origin);
	}

	public static boolean isActive() {
		return viewportActive;
	}

	public static CameraData getSlot(int index) {
		if (index < 0 || index > 1) return new CameraData(false, Vec3d.ZERO);
		return slots[index];
	}

	public static void reset() {
		viewportActive = false;
		origin = null;
		slots[0] = new CameraData(false, Vec3d.ZERO);
		slots[1] = new CameraData(false, Vec3d.ZERO);
	}
}
