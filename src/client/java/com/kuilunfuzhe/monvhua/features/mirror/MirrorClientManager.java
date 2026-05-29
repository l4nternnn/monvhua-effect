package com.kuilunfuzhe.monvhua.features.mirror;

import com.kuilunfuzhe.monvhua.network.mirror.MirrorStateS2CPacket;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

public class MirrorClientManager {
	private static boolean viewportActive = false;
	private static final CameraData[] slots = new CameraData[2];

	// Charging state (for HUD bar)
	private static int currentCharge = 0;
	private static int maxCharge = 0;

	/** 镜面视角硬偏移，修改此处数值调整观看位置 */
	private static final Vec3d VIEWPORT_OFFSET = new Vec3d(0, 1.640, 0);

	static {
		slots[0] = new CameraData(false, Vec3d.ZERO, Vec3d.ZERO, 0);
		slots[1] = new CameraData(false, Vec3d.ZERO, Vec3d.ZERO, 0);
	}

	public record CameraData(boolean active, Vec3d hsPos, Vec3d mapPos, double radius) {}

	public static void onStatePacket(MirrorStateS2CPacket packet) {
		viewportActive = packet.viewportActive();

		slots[0] = new CameraData(
			packet.slot1Active(),
			packet.getHsPos1() != null ? packet.getHsPos1() : Vec3d.ZERO,
			packet.getMapPos1() != null ? packet.getMapPos1() : Vec3d.ZERO,
			packet.radius1()
		);
		slots[1] = new CameraData(
			packet.slot2Active(),
			packet.getHsPos2() != null ? packet.getHsPos2() : Vec3d.ZERO,
			packet.getMapPos2() != null ? packet.getMapPos2() : Vec3d.ZERO,
			packet.radius2()
		);
	}

	/**
	 * 计算镜面视角位置 = mapPos + (playerPos - hsPos) + VIEWPORT_OFFSET
	 * 玩家相对于 hs_发生点的位移叠加上 map_映射点，再加上硬偏移
	 */
	public static Vec3d getSlotWorldPos(int slot, Vec3d playerPos) {
		CameraData data = getSlot(slot);
		if (!data.active()) return null;
		return data.mapPos().add(playerPos.subtract(data.hsPos())).add(VIEWPORT_OFFSET);
	}

	public static boolean isActive() {
		return viewportActive;
	}

	public static CameraData getSlot(int index) {
		if (index < 0 || index > 1) return new CameraData(false, Vec3d.ZERO, Vec3d.ZERO, 0);
		return slots[index];
	}

	public static void setCharge(int current, int max) {
		currentCharge = current;
		maxCharge = max;
	}

	public static int getCurrentCharge() { return currentCharge; }
	public static int getMaxCharge() { return maxCharge; }
	public static boolean isCharging() { return currentCharge > 0 && maxCharge > 0; }
	public static float getChargeRatio() {
		if (maxCharge <= 0) return 0;
		return Math.min(1.0f, (float) currentCharge / maxCharge);
	}

	public static void reset() {
		viewportActive = false;
		slots[0] = new CameraData(false, Vec3d.ZERO, Vec3d.ZERO, 0);
		slots[1] = new CameraData(false, Vec3d.ZERO, Vec3d.ZERO, 0);
		currentCharge = 0;
		maxCharge = 0;
	}
}
