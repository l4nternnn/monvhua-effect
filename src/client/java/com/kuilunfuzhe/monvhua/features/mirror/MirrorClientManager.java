package com.kuilunfuzhe.monvhua.features.mirror;

import com.kuilunfuzhe.monvhua.network.mirror.MirrorPackets.StateS2C;
import net.minecraft.util.math.Vec3d;

/**
 * 镜像客户端状态管理器。
 * 维护两个CameraData槽位（对应两个镜像发生点）、蓄力状态数据，
 * 并提供坐标计算逻辑：将玩家相对于发生点的位移映射到地图映射点。
 */
public class MirrorClientManager {
	/** 镜像视口是否激活 */
	private static boolean viewportActive = false;
	/** 两个镜面摄像机数据槽位 */
	private static final CameraData[] slots = new CameraData[2];

	/** 当前蓄力值（用于HUD蓄力条） */
	private static int currentCharge = 0;
	/** 最大蓄力值 */
	private static int maxCharge = 0;

	/** 镜面视角硬偏移，修改此处数值调整观看位置 */
	private static final Vec3d VIEWPORT_OFFSET = new Vec3d(0, 1.640, 0);

	static {
		slots[0] = new CameraData(false, Vec3d.ZERO, Vec3d.ZERO, 0);
		slots[1] = new CameraData(false, Vec3d.ZERO, Vec3d.ZERO, 0);
	}

	/**
	 * 摄像机数据记录。
	 * @param active 槽位是否激活
	 * @param hsPos  镜像发生点坐标
	 * @param mapPos 地图映射点坐标
	 * @param radius 镜像半径
	 */
	public record CameraData(boolean active, Vec3d hsPos, Vec3d mapPos, double radius) {}

	/**
	 * 接收服务器推送的镜像状态数据包，更新所有槽位和视口状态。
	 * @param packet 镜面状态S2C数据包
	 */
	public static void onStatePacket(StateS2C packet) {
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
	 * 玩家相对于hs发生点的位移叠加上map映射点，再加上硬偏移。
	 *
	 * @param slot      槽位索引（0或1）
	 * @param playerPos 玩家当前坐标
	 * @return 镜面视角的世界坐标，槽位未激活时返回null
	 */
	public static Vec3d getSlotWorldPos(int slot, Vec3d playerPos) {
		CameraData data = getSlot(slot);
		if (!data.active()) return null;
		return data.mapPos().add(playerPos.subtract(data.hsPos())).add(VIEWPORT_OFFSET);
	}

	public static Vec3d getActiveSlotWorldPos(Vec3d playerPos) {
		CameraData data = getActiveSlot(playerPos);
		if (data == null) return null;
		return data.mapPos().add(playerPos.subtract(data.hsPos())).add(VIEWPORT_OFFSET);
	}

	public static CameraData getActiveSlot(Vec3d playerPos) {
		for (CameraData data : slots) {
			if (data.active() && playerPos.distanceTo(data.hsPos()) <= data.radius()) {
				return data;
			}
		}
		for (CameraData data : slots) {
			if (data.active()) return data;
		}
		return null;
	}

	/**
	 * 判断镜像视口是否激活。
	 * @return true表示视口正在渲染
	 */
	public static boolean isActive() {
		return viewportActive;
	}

	/**
	 * 获取指定索引槽位的CameraData，越界时返回空数据。
	 * @param index 槽位索引（0或1）
	 * @return 摄像机数据
	 */
	public static CameraData getSlot(int index) {
		if (index < 0 || index > 1) return new CameraData(false, Vec3d.ZERO, Vec3d.ZERO, 0);
		return slots[index];
	}

	/**
	 * 设置蓄力数据（由网络接收器调用）。
	 * @param current 当前蓄力值
	 * @param max     最大蓄力值
	 */
	public static void setCharge(int current, int max) {
		currentCharge = current;
		maxCharge = max;
	}

	public static int getCurrentCharge() { return currentCharge; }
	public static int getMaxCharge() { return maxCharge; }

	/** @return 是否有蓄力进度 */
	public static boolean isCharging() { return currentCharge > 0 && maxCharge > 0; }

	/** @return 蓄力比例（0.0 ~ 1.0） */
	public static float getChargeRatio() {
		if (maxCharge <= 0) return 0;
		return Math.min(1.0f, (float) currentCharge / maxCharge);
	}

	/** 重置所有镜面状态到初始值 */
	public static void reset() {
		viewportActive = false;
		slots[0] = new CameraData(false, Vec3d.ZERO, Vec3d.ZERO, 0);
		slots[1] = new CameraData(false, Vec3d.ZERO, Vec3d.ZERO, 0);
		currentCharge = 0;
		maxCharge = 0;
	}
}
