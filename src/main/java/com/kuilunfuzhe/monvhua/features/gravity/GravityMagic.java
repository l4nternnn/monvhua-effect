package com.kuilunfuzhe.monvhua.features.gravity;

import com.kuilunfuzhe.monvhua.network.gravity.GravityPackets;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GravityMagic {
    public static final double DEFAULT_GRAVITY = 0.04D;
    public static final double MIN_GRAVITY = 0.0D;
    public static final double MAX_GRAVITY = 0.30D;

    private static final int GROUP_RADIUS = 1;
    private static final double UP_SPEED = 0.72D;
    private static final double RIGHT_SPEED = 0.82D;
    private static final Map<UUID, LaunchMode> PLAYER_MODES = new ConcurrentHashMap<>();
    private static final Map<UUID, Double> PLAYER_GRAVITY = new ConcurrentHashMap<>();
    private static final Map<UUID, Double> ENTITY_GRAVITY = new ConcurrentHashMap<>();

    public enum LaunchMode {
        UP,
        RIGHT
    }

    private GravityMagic() {
    }

    public static void initialize() {
        ServerPlayNetworking.registerGlobalReceiver(GravityPackets.AdjustGravityC2S.ID, (packet, context) -> {
            context.server().execute(() -> {
                ServerPlayerEntity player = context.player();
                double gravity = adjustTargetGravity(player, packet.entityId(), packet.gravity());
                player.sendMessage(Text.literal("\u00a7b[Gravity] g=" + format(gravity)), true);
            });
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerWorld world : server.getWorlds()) {
                applyEntityGravity(world);
            }
        });
    }

    public static LaunchMode toggleMode(ServerPlayerEntity player) {
        LaunchMode next = getMode(player) == LaunchMode.UP ? LaunchMode.RIGHT : LaunchMode.UP;
        PLAYER_MODES.put(player.getUuid(), next);
        player.sendMessage(Text.literal("\u00a7b[Gravity] Mode: " + displayName(next)), true);
        return next;
    }

    public static LaunchMode getMode(ServerPlayerEntity player) {
        return PLAYER_MODES.getOrDefault(player.getUuid(), LaunchMode.UP);
    }

    public static double getSelectedGravity(ServerPlayerEntity player) {
        return PLAYER_GRAVITY.getOrDefault(player.getUuid(), DEFAULT_GRAVITY);
    }

    public static double setSelectedGravity(ServerPlayerEntity player, double gravity) {
        double clamped = clampGravity(gravity);
        PLAYER_GRAVITY.put(player.getUuid(), clamped);
        return clamped;
    }

    public static double adjustTargetGravity(ServerPlayerEntity player, int entityId, double gravity) {
        double clamped = clampGravity(gravity);
        if (entityId >= 0) {
            Entity entity = player.getWorld().getEntityById(entityId);
            if (entity instanceof GravityBlockEntity gravityBlock) {
                gravityBlock.setGravityAmount(clamped);
            } else if (entity != null) {
                ENTITY_GRAVITY.put(entity.getUuid(), clamped);
                entity.setNoGravity(true);
            } else {
                setSelectedGravity(player, clamped);
            }
        } else {
            setSelectedGravity(player, clamped);
        }
        return clamped;
    }

    public static int launch(ServerWorld world, ServerPlayerEntity player, BlockPos origin, boolean group) {
        LaunchMode mode = getMode(player);
        Vec3d velocity = velocityFor(player, mode);
        double gravity = getSelectedGravity(player);
        int launched = 0;

        if (group) {
            for (BlockPos pos : BlockPos.iterateOutwards(origin, GROUP_RADIUS, GROUP_RADIUS, GROUP_RADIUS)) {
                if (launchOne(world, pos.toImmutable(), velocity, gravity)) {
                    launched++;
                }
            }
        } else if (launchOne(world, origin, velocity, gravity)) {
            launched = 1;
        }

        if (launched > 0) {
            player.sendMessage(Text.literal("\u00a7b[Gravity] Launched " + launched + " block(s) "
                    + displayName(mode) + " g=" + format(gravity)), true);
        } else {
            player.sendMessage(Text.literal("\u00a7c[Gravity] No movable blocks"), true);
        }
        return launched;
    }

    private static boolean launchOne(ServerWorld world, BlockPos pos, Vec3d velocity, double gravity) {
        BlockState state = world.getBlockState(pos);
        if (!canMove(world, pos, state)) {
            return false;
        }

        world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        GravityBlockEntity entity = new GravityBlockEntity(
                world,
                pos.getX() + 0.5D,
                pos.getY(),
                pos.getZ() + 0.5D,
                state,
                velocity,
                gravity
        );
        world.spawnEntity(entity);
        return true;
    }

    private static boolean canMove(ServerWorld world, BlockPos pos, BlockState state) {
        if (state.isAir() || state.isOf(Blocks.BEDROCK) || state.isOf(Blocks.BARRIER)) {
            return false;
        }
        if (state.getHardness(world, pos) < 0) {
            return false;
        }
        BlockEntity blockEntity = world.getBlockEntity(pos);
        return blockEntity == null;
    }

    private static Vec3d velocityFor(ServerPlayerEntity player, LaunchMode mode) {
        if (mode == LaunchMode.UP) {
            return new Vec3d(0.0D, UP_SPEED, 0.0D);
        }

        double yawRad = Math.toRadians(player.getYaw() + 90.0F);
        return new Vec3d(Math.cos(yawRad) * RIGHT_SPEED, 0.08D, Math.sin(yawRad) * RIGHT_SPEED);
    }

    private static void applyEntityGravity(ServerWorld world) {
        ENTITY_GRAVITY.entrySet().removeIf(entry -> {
            Entity entity = world.getEntity(entry.getKey());
            if (entity == null || !entity.isAlive()) {
                return true;
            }
            if (entity instanceof GravityBlockEntity) {
                return false;
            }
            entity.setNoGravity(true);
            entity.setVelocity(entity.getVelocity().add(0.0D, -entry.getValue(), 0.0D));
            return false;
        });
    }

    public static double clampGravity(double gravity) {
        return Math.clamp(gravity, MIN_GRAVITY, MAX_GRAVITY);
    }

    public static String format(double gravity) {
        return String.format("%.3f", gravity);
    }

    private static String displayName(LaunchMode mode) {
        return mode == LaunchMode.UP ? "UP" : "RIGHT";
    }
}
