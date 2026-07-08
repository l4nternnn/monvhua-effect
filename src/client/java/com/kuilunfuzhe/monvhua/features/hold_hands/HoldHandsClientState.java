package com.kuilunfuzhe.monvhua.features.hold_hands;

import com.kuilunfuzhe.monvhua.network.hold_hands.HoldHandsSyncS2CPacket;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class HoldHandsClientState {
    private static final Map<Integer, HoldHandData> ACTIVE = new ConcurrentHashMap<>();
    private static final Map<Long, PairPoint> PAIR_POINTS = new ConcurrentHashMap<>();
    private static final float FALLBACK_DEFAULT_DISTANCE = 1.012668F;
    private static final double CLIENT_PAIR_RAW_EPSILON = 0.0005D;
    private static final double CLIENT_POINT_DEADBAND = 0.025D;
    private static final double CLIENT_POINT_ALPHA = 0.36D;
    private static final double CLIENT_POINT_FAST_DISTANCE = 0.60D;

    private HoldHandsClientState() {
    }

    public static void apply(HoldHandsSyncS2CPacket packet) {
        if (!packet.active()) {
            HoldHandData previous = ACTIVE.get(packet.entityId());
            if (previous != null) {
                PAIR_POINTS.remove(pairKey(packet.entityId(), previous.partnerId()));
            }
            ACTIVE.remove(packet.entityId());
            return;
        }
        HoldHandsSkeletalPose.HandSide handSide = packet.handSide() == HoldHandsSyncS2CPacket.HAND_LEFT
                ? HoldHandsSkeletalPose.HandSide.LEFT
                : HoldHandsSkeletalPose.HandSide.RIGHT;
        Vec3d incomingPoint = new Vec3d(packet.sharedHandX(), packet.sharedHandY(), packet.sharedHandZ());
        long pairKey = pairKey(packet.entityId(), packet.partnerId());
        PairPoint previousPair = PAIR_POINTS.get(pairKey);
        Vec3d sharedHandPoint = stabilizePairPoint(previousPair, incomingPoint);
        PAIR_POINTS.put(pairKey, new PairPoint(incomingPoint, sharedHandPoint));
        ACTIVE.put(packet.entityId(), new HoldHandData(handSide, packet.partnerId(),
                packet.defaultDistance(), packet.holdBodyYaw(), sharedHandPoint));
    }

    public static void clear() {
        ACTIVE.clear();
        PAIR_POINTS.clear();
    }

    public static boolean isHoldingHands(int entityId) {
        return ACTIVE.containsKey(entityId);
    }

    public static boolean isFollower(int entityId) {
        HoldHandData data = ACTIVE.get(entityId);
        return data != null && HoldHandsSkeletalPose.isFollowerHand(data.handSide());
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

    public static float getHoldBodyYaw(int entityId, float fallback) {
        HoldHandData data = ACTIVE.get(entityId);
        return data != null ? data.holdBodyYaw() : fallback;
    }

    public static Vec3d getSharedHandPoint(int entityId) {
        HoldHandData data = ACTIVE.get(entityId);
        if (data == null) {
            return null;
        }
        PairPoint pairPoint = PAIR_POINTS.get(pairKey(entityId, data.partnerId()));
        return pairPoint != null ? pairPoint.sharedHandPoint() : data.sharedHandPoint();
    }

    private static Vec3d stabilizePairPoint(PairPoint previousPair, Vec3d incoming) {
        if (previousPair == null) {
            return stabilizeClientPoint(null, incoming);
        }
        if (previousPair.rawPoint().squaredDistanceTo(incoming) <= CLIENT_PAIR_RAW_EPSILON * CLIENT_PAIR_RAW_EPSILON) {
            return previousPair.sharedHandPoint();
        }
        return stabilizeClientPoint(previousPair.sharedHandPoint(), incoming);
    }

    private static long pairKey(int entityId, int partnerId) {
        int otherId = partnerId == HoldHandsSyncS2CPacket.NO_PARTNER ? entityId : partnerId;
        int low = Math.min(entityId, otherId);
        int high = Math.max(entityId, otherId);
        return ((long) low << 32) ^ (high & 0xffffffffL);
    }

    private static Vec3d stabilizeClientPoint(Vec3d previous, Vec3d incoming) {
        if (incoming == null) {
            return null;
        }
        if (previous == null || previous.lengthSquared() <= 0.000001D) {
            return incoming;
        }

        double distance = previous.distanceTo(incoming);
        if (distance <= CLIENT_POINT_DEADBAND) {
            return previous;
        }
        if (distance >= CLIENT_POINT_FAST_DISTANCE) {
            return incoming;
        }

        double t = Math.min(1.0D, Math.max(0.0D,
                (distance - CLIENT_POINT_DEADBAND) / Math.max(0.000001D, CLIENT_POINT_FAST_DISTANCE - CLIENT_POINT_DEADBAND)));
        double alpha = CLIENT_POINT_ALPHA + (0.55D - CLIENT_POINT_ALPHA) * t * t * (3.0D - 2.0D * t);
        return previous.lerp(incoming, alpha);
    }

    private record HoldHandData(HoldHandsSkeletalPose.HandSide handSide, int partnerId,
                                 float defaultDistance, float holdBodyYaw, Vec3d sharedHandPoint) {
    }

    private record PairPoint(Vec3d rawPoint, Vec3d sharedHandPoint) {
    }
}
