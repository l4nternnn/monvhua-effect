package com.kuilunfuzhe.monvhua.features.carryentity;

import com.kuilunfuzhe.monvhua.network.carryentity.CarryPoseSyncS2CPacket;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CarryPoseClientState {
	private static final Map<Integer, PoseData> POSES = new ConcurrentHashMap<>();

	private CarryPoseClientState() {
	}

	public static void apply(CarryPoseSyncS2CPacket packet) {
		if (packet.pose() == CarryPoseSyncS2CPacket.POSE_NONE) {
			POSES.remove(packet.entityId());
			return;
		}
		POSES.put(packet.entityId(), new PoseData(packet.pose(), packet.partnerId()));
	}

	public static void clear() {
		POSES.clear();
	}

	public static boolean isCarrier(int entityId) {
		PoseData data = POSES.get(entityId);
		return data != null && data.pose == CarryPoseSyncS2CPacket.POSE_CARRIER;
	}

	public static boolean isCarried(int entityId) {
		PoseData data = POSES.get(entityId);
		return data != null && data.pose == CarryPoseSyncS2CPacket.POSE_CARRIED;
	}

	public static int getCarrierId(int entityId) {
		PoseData data = POSES.get(entityId);
		return data != null ? data.partnerId : -1;
	}

	public static int getPartnerId(int entityId) {
		PoseData data = POSES.get(entityId);
		return data != null ? data.partnerId : -1;
	}

	public record PoseData(int pose, int partnerId) {
	}
}
