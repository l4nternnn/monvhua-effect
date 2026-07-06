package com.kuilunfuzhe.monvhua.features.hold_hands;

import com.kuilunfuzhe.monvhua.network.hold_hands.HoldHandsSyncS2CPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HoldHandsManager {
    private static final double MODEL_HIRO_TO_EMA_SIDE_OFFSET = -1.008708D;
    private static final double MODEL_HIRO_TO_EMA_FORWARD_OFFSET = -0.089465946D;
    private static final double DEFAULT_FOLLOW_SIDE_OFFSET = -MODEL_HIRO_TO_EMA_SIDE_OFFSET;
    private static final double DEFAULT_FOLLOW_FORWARD_OFFSET = -MODEL_HIRO_TO_EMA_FORWARD_OFFSET;
    private static final double FOLLOW_STOP_DISTANCE = 0.08D;
    private static final double FOLLOW_PULL_DISTANCE = 0.35D;
    private static final double FOLLOW_TELEPORT_DISTANCE = 3.0D;
    private static final double FOLLOW_MAX_SPEED = 0.35D;
    private static final double FOLLOW_SMOOTHING = 0.45D;

    private static final Map<UUID, HoldHandData> ACTIVE = new ConcurrentHashMap<>();

    private HoldHandsManager() {
    }

    public static boolean isHoldingHands(ServerPlayerEntity player) {
        return player != null && ACTIVE.containsKey(player.getUuid());
    }

    public static void togglePair(ServerPlayerEntity initiator, ServerPlayerEntity target) {
        if (initiator == null || target == null || initiator == target) {
            return;
        }

        HoldHandData initiatorData = ACTIVE.get(initiator.getUuid());
        if (initiatorData != null && target.getUuid().equals(initiatorData.partnerUuid())) {
            stopPair(initiator);
            initiator.sendMessage(Text.literal("已解除牵手"), true);
            target.sendMessage(Text.literal("已解除牵手"), true);
            return;
        }

        stopPair(initiator);
        stopPair(target);
        startPair(initiator, target);
        initiator.sendMessage(Text.literal("已与 " + target.getName().getString() + " 牵手"), true);
        target.sendMessage(Text.literal("已与 " + initiator.getName().getString() + " 牵手"), true);
    }

    private static void startPair(ServerPlayerEntity initiator, ServerPlayerEntity target) {
        ACTIVE.put(initiator.getUuid(), new HoldHandData(HoldHandsSkeletalPose.HandSide.LEFT, target.getUuid()));
        ACTIVE.put(target.getUuid(), new HoldHandData(HoldHandsSkeletalPose.HandSide.RIGHT, initiator.getUuid()));
        sync(initiator, true);
        sync(target, true);
    }

    private static void stopPair(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }

        HoldHandData data = ACTIVE.remove(player.getUuid());
        sync(player, false);
        if (data == null) {
            return;
        }

        ServerPlayerEntity partner = getPlayer(player.getServer(), data.partnerUuid());
        if (partner != null) {
            ACTIVE.remove(partner.getUuid());
            sync(partner, false);
        }
    }

    public static void cleanupForDisconnect(ServerPlayerEntity player) {
        stopPair(player);
    }

    public static void tick(MinecraftServer server) {
        if (server == null || ACTIVE.isEmpty()) {
            return;
        }

        for (ServerPlayerEntity follower : server.getPlayerManager().getPlayerList()) {
            HoldHandData data = ACTIVE.get(follower.getUuid());
            if (data == null || data.handSide() != HoldHandsSkeletalPose.HandSide.RIGHT) {
                continue;
            }

            ServerPlayerEntity leader = getPlayer(server, data.partnerUuid());
            if (!canKeepHolding(follower, leader)) {
                stopPair(follower);
                continue;
            }

            moveFollowerTowardDefaultSlot(leader, follower);
        }
    }

    public static void syncAllTo(ServerPlayerEntity receiver) {
        MinecraftServer server = receiver.getServer();
        if (server == null) {
            return;
        }

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            HoldHandData data = ACTIVE.get(player.getUuid());
            if (data != null) {
                ServerPlayNetworking.send(receiver, createPacket(server, player, true, data));
            }
        }
    }

    private static void sync(ServerPlayerEntity player, boolean active) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        HoldHandData data = ACTIVE.get(player.getUuid());
        HoldHandsSyncS2CPacket packet = createPacket(server, player, active, data);
        for (ServerPlayerEntity receiver : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(receiver, packet);
        }
    }

    private static HoldHandsSyncS2CPacket createPacket(MinecraftServer server, ServerPlayerEntity player,
                                                       boolean active, HoldHandData data) {
        int side = data != null && data.handSide() == HoldHandsSkeletalPose.HandSide.LEFT
                ? HoldHandsSyncS2CPacket.HAND_LEFT
                : HoldHandsSyncS2CPacket.HAND_RIGHT;
        ServerPlayerEntity partner = data != null ? getPlayer(server, data.partnerUuid()) : null;
        int partnerId = partner != null ? partner.getId() : HoldHandsSyncS2CPacket.NO_PARTNER;
        return new HoldHandsSyncS2CPacket(player.getId(), active, side, partnerId);
    }

    private static ServerPlayerEntity getPlayer(MinecraftServer server, UUID uuid) {
        return server != null && uuid != null ? server.getPlayerManager().getPlayer(uuid) : null;
    }

    private static boolean canKeepHolding(ServerPlayerEntity follower, ServerPlayerEntity leader) {
        return follower != null
                && leader != null
                && follower.isAlive()
                && leader.isAlive()
                && follower.getWorld() == leader.getWorld();
    }

    private static void moveFollowerTowardDefaultSlot(ServerPlayerEntity leader, ServerPlayerEntity follower) {
        Vec3d desired = leader.getPos().add(getDefaultFollowerOffset(leader));
        Vec3d delta = desired.subtract(follower.getPos());
        double distance = delta.horizontalLength();

        if (distance <= FOLLOW_STOP_DISTANCE) {
            return;
        }

        if (distance >= FOLLOW_TELEPORT_DISTANCE) {
            follower.requestTeleport(desired.x, follower.getY(), desired.z);
            follower.setVelocity(Vec3d.ZERO);
            return;
        }

        if (distance < FOLLOW_PULL_DISTANCE) {
            return;
        }

        Vec3d horizontalPull = clampHorizontalSpeed(new Vec3d(delta.x, 0.0D, delta.z)
                .multiply(FOLLOW_SMOOTHING), FOLLOW_MAX_SPEED);
        Vec3d currentVelocity = follower.getVelocity();
        follower.setVelocity(horizontalPull.x, currentVelocity.y, horizontalPull.z);
        follower.velocityModified = true;
    }

    private static Vec3d clampHorizontalSpeed(Vec3d velocity, double maxSpeed) {
        double length = velocity.horizontalLength();
        if (length <= maxSpeed || length <= 0.000001D) {
            return velocity;
        }
        double scale = maxSpeed / length;
        return new Vec3d(velocity.x * scale, velocity.y, velocity.z * scale);
    }

    private static Vec3d getDefaultFollowerOffset(ServerPlayerEntity leader) {
        double yawRad = Math.toRadians(leader.getYaw());
        Vec3d right = new Vec3d(Math.cos(yawRad), 0.0D, Math.sin(yawRad));
        Vec3d forward = new Vec3d(-Math.sin(yawRad), 0.0D, Math.cos(yawRad));
        return right.multiply(DEFAULT_FOLLOW_SIDE_OFFSET).add(forward.multiply(DEFAULT_FOLLOW_FORWARD_OFFSET));
    }

    private record HoldHandData(HoldHandsSkeletalPose.HandSide handSide, UUID partnerUuid) {
    }
}
