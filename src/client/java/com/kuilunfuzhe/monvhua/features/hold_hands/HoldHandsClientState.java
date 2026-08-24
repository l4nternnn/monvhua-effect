package com.kuilunfuzhe.monvhua.features.hold_hands;

import com.kuilunfuzhe.monvhua.network.hold_hands.HoldHandsSyncS2CPacket;
import com.kuilunfuzhe.monvhua.network.SafeClientNetworking;
import com.kuilunfuzhe.monvhua.network.hold_hands.HoldHandsInputC2SPacket;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class HoldHandsClientState {
    private static final Map<Integer, HoldHandData> ACTIVE = new ConcurrentHashMap<>();
    private static final Map<Long, PairPoint> PAIR_POINTS = new ConcurrentHashMap<>();
    private static final Map<Integer, Long> LAST_SEQUENCE = new ConcurrentHashMap<>();
    private static final float FALLBACK_DEFAULT_DISTANCE = 1.012668F;
    private static final double CLIENT_PAIR_RAW_EPSILON = 0.0005D;
    private static final double CLIENT_POINT_DEADBAND = 0.025D;
    private static final double CLIENT_POINT_ALPHA = 0.36D;
    private static final double CLIENT_POINT_FAST_DISTANCE = 0.60D;
    private static final double CLIENT_MAX_POINT_COORD = 30_000_000.0D;
    private static final double CLIENT_MAX_VELOCITY = 4.0D;
    private static int inputSequence;

    private HoldHandsClientState() {
    }

    public static void apply(HoldHandsSyncS2CPacket packet) {
        if (packet == null || !Float.isFinite(packet.sharedHandX()) || !Float.isFinite(packet.sharedHandY())
                || !Float.isFinite(packet.sharedHandZ()) || !Float.isFinite(packet.velocityX())
                || !Float.isFinite(packet.velocityY()) || !Float.isFinite(packet.velocityZ())
                || !Float.isFinite(packet.defaultDistance()) || !Float.isFinite(packet.holdBodyYaw())) {
            return;
        }
        if (Math.abs(packet.sharedHandX()) > CLIENT_MAX_POINT_COORD
                || Math.abs(packet.sharedHandY()) > CLIENT_MAX_POINT_COORD
                || Math.abs(packet.sharedHandZ()) > CLIENT_MAX_POINT_COORD
                || Math.abs(packet.velocityX()) > CLIENT_MAX_VELOCITY
                || Math.abs(packet.velocityY()) > CLIENT_MAX_VELOCITY
                || Math.abs(packet.velocityZ()) > CLIENT_MAX_VELOCITY) {
            return;
        }
        if ((packet.role() != HoldHandsSyncS2CPacket.ROLE_ACTIVE
                && packet.role() != HoldHandsSyncS2CPacket.ROLE_PASSIVE)
                || (packet.handSide() != HoldHandsSyncS2CPacket.HAND_LEFT
                && packet.handSide() != HoldHandsSyncS2CPacket.HAND_RIGHT)
                || packet.entityId() < 0 || packet.partnerId() == packet.entityId()) {
            return;
        }
        long previousSequence = LAST_SEQUENCE.getOrDefault(packet.entityId(), Long.MIN_VALUE);
        if (packet.sequence() < previousSequence) {
            return;
        }
        LAST_SEQUENCE.put(packet.entityId(), packet.sequence());
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
        boolean passive = packet.role() == HoldHandsSyncS2CPacket.ROLE_PASSIVE;
        Vec3d incomingPoint = new Vec3d(packet.sharedHandX(), packet.sharedHandY(), packet.sharedHandZ());
        float tension = clamp01(packet.tension());
        long pairKey = pairKey(packet.entityId(), packet.partnerId());
        PairPoint previousPair = PAIR_POINTS.get(pairKey);
        if (previousPair != null && packet.serverTick() < previousPair.serverTick()) {
            return;
        }
        Vec3d sharedHandPoint = stabilizePairPoint(previousPair, incomingPoint, tension);
        float visualTension = previousPair == null ? tension
                : previousPair.visualTension() + (tension - previousPair.visualTension()) * 0.28F;
        int predictionTicks = previousPair != null && packet.serverTick() == previousPair.serverTick()
                ? previousPair.predictionTicks() : 0;
        PAIR_POINTS.put(pairKey, new PairPoint(incomingPoint, sharedHandPoint,
                new Vec3d(packet.velocityX(), packet.velocityY(), packet.velocityZ()), packet.serverTick(), predictionTicks,
                tension, visualTension));
        float defaultDistance = MathHelper.clamp(packet.defaultDistance(), 0.82F, 1.26F);
        float holdBodyYaw = MathHelper.wrapDegrees(packet.holdBodyYaw());
        ACTIVE.put(packet.entityId(), new HoldHandData(passive, handSide, packet.partnerId(),
                defaultDistance, holdBodyYaw, sharedHandPoint));
    }

    public static void clear() {
        ACTIVE.clear();
        PAIR_POINTS.clear();
        LAST_SEQUENCE.clear();
        inputSequence = 0;
    }

    /** Sends only local input intent. Position and velocity never come from the client. */
    public static void tickInput(MinecraftClient client) {
        if (client == null || client.player == null || client.getNetworkHandler() == null
                || !isHoldingHands(client.player.getId())) {
            return;
        }
        PlayerInput input = client.player.input.playerInput;
        SafeClientNetworking.send(new HoldHandsInputC2SPacket(
                client.world == null ? 0L : client.world.getTime(), inputSequence++,
                input.forward(), input.backward(), input.left(), input.right(),
                input.jump(), input.sneak(), input.sprint(), client.player.getYaw()));
    }

    /** Bounded visual prediction of the shared hand point; entity positions remain server-authoritative. */
    public static void tickPrediction(MinecraftClient client) {
        if (client == null || client.world == null || PAIR_POINTS.isEmpty()) {
            return;
        }
        for (Map.Entry<Long, PairPoint> entry : PAIR_POINTS.entrySet()) {
            PairPoint point = entry.getValue();
            if (point.predictionTicks() >= 2) {
                continue;
            }
            Vec3d velocity = point.velocity();
            if (velocity == null || velocity.lengthSquared() <= 0.000001D) {
                continue;
            }
            Vec3d step = HoldHandsLinkGeometry.clampSpeed(velocity, 0.08D);
            PairPoint predicted = new PairPoint(point.rawPoint(), point.sharedHandPoint().add(step), velocity,
                    point.serverTick(), point.predictionTicks() + 1, point.tension(), point.visualTension());
            PAIR_POINTS.replace(entry.getKey(), point, predicted);
        }
    }

    public static boolean isHoldingHands(int entityId) {
        return ACTIVE.containsKey(entityId);
    }

    public static boolean isFollower(int entityId) {
        HoldHandData data = ACTIVE.get(entityId);
        return data != null && data.passive();
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

    public static float getTension(int entityId) {
        HoldHandData data = ACTIVE.get(entityId);
        if (data == null) {
            return 0.0F;
        }
        PairPoint point = PAIR_POINTS.get(pairKey(entityId, data.partnerId()));
        return point == null ? 0.0F : point.visualTension();
    }

    private static Vec3d stabilizePairPoint(PairPoint previousPair, Vec3d incoming, float tension) {
        if (previousPair == null) {
            return stabilizeClientPoint(null, incoming, tension);
        }
        if (previousPair.rawPoint().squaredDistanceTo(incoming) <= CLIENT_PAIR_RAW_EPSILON * CLIENT_PAIR_RAW_EPSILON) {
            return previousPair.sharedHandPoint();
        }
        return stabilizeClientPoint(previousPair.sharedHandPoint(), incoming, tension);
    }

    private static long pairKey(int entityId, int partnerId) {
        int otherId = partnerId == HoldHandsSyncS2CPacket.NO_PARTNER ? entityId : partnerId;
        int low = Math.min(entityId, otherId);
        int high = Math.max(entityId, otherId);
        return ((long) low << 32) ^ (high & 0xffffffffL);
    }

    private static Vec3d stabilizeClientPoint(Vec3d previous, Vec3d incoming, float tension) {
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
        double tensionAlpha = 0.35D + 0.45D * clamp01(tension);
        double alpha = Math.max(tensionAlpha,
                CLIENT_POINT_ALPHA + (0.55D - CLIENT_POINT_ALPHA) * t * t * (3.0D - 2.0D * t));
        return previous.lerp(incoming, alpha);
    }

    private static float clamp01(float value) {
        return Float.isFinite(value) ? Math.max(0.0F, Math.min(1.0F, value)) : 0.0F;
    }

    private record HoldHandData(boolean passive, HoldHandsSkeletalPose.HandSide handSide, int partnerId,
                                 float defaultDistance, float holdBodyYaw, Vec3d sharedHandPoint) {
    }

    private record PairPoint(Vec3d rawPoint, Vec3d sharedHandPoint, Vec3d velocity,
                             long serverTick, int predictionTicks, float tension, float visualTension) {
    }
}
