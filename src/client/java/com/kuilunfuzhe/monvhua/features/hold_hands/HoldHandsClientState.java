package com.kuilunfuzhe.monvhua.features.hold_hands;

import com.kuilunfuzhe.monvhua.network.hold_hands.HoldHandsSyncS2CPacket;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class HoldHandsClientState {
    private static final Map<Integer, HoldHandData> ACTIVE = new ConcurrentHashMap<>();
    private static final float FALLBACK_DEFAULT_DISTANCE = 1.012668F;

    private HoldHandsClientState() {
    }

    public static void apply(HoldHandsSyncS2CPacket packet) {
        if (!packet.active()) {
            ACTIVE.remove(packet.entityId());
            return;
        }
        HoldHandsSkeletalPose.HandSide handSide = packet.handSide() == HoldHandsSyncS2CPacket.HAND_LEFT
                ? HoldHandsSkeletalPose.HandSide.LEFT
                : HoldHandsSkeletalPose.HandSide.RIGHT;
        ACTIVE.put(packet.entityId(), new HoldHandData(handSide, packet.partnerId(), packet.defaultDistance()));
    }

    public static void clear() {
        ACTIVE.clear();
    }

    public static boolean isHoldingHands(int entityId) {
        return ACTIVE.containsKey(entityId);
    }

    public static HoldHandsSkeletalPose.HandSide getHandSide(int entityId) {
        HoldHandData data = ACTIVE.get(entityId);
        return data != null ? data.handSide() : HoldHandsSkeletalPose.HandSide.RIGHT;
    }

    public static int getPartnerId(int entityId) {
        HoldHandData data = ACTIVE.get(entityId);
        return data != null ? data.partnerId() : HoldHandsSyncS2CPacket.NO_PARTNER;
    }

    public static float getDefaultDistance(int entityId) {
        HoldHandData data = ACTIVE.get(entityId);
        return data != null ? Math.max(0.001F, data.defaultDistance()) : FALLBACK_DEFAULT_DISTANCE;
    }

    private record HoldHandData(HoldHandsSkeletalPose.HandSide handSide, int partnerId, float defaultDistance) {
    }
}
