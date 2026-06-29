package com.kuilunfuzhe.monvhua.features.injured_and_bleeding;

import com.kuilunfuzhe.monvhua.item.config.InjuredBleedingConfig;
import com.kuilunfuzhe.monvhua.network.injured_and_bleeding.InjuredBleedingPackets;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class InjuredBleedingFeature {
    private static final double SYNC_DISTANCE_SQUARED = 96.0D * 96.0D;
    private static final double LANDING_RADIUS = 0.6D;
    private static final double BALLISTIC_GRAVITY_PER_TICK = -0.04D;
    private static final int DEFAULT_FADE_TICKS = 120;

    private InjuredBleedingFeature() {
    }

    public static void initialize() {
        ServerPlayNetworking.registerGlobalReceiver(InjuredBleedingPackets.RequestConfigC2S.ID, (packet, context) ->
                context.server().execute(() -> syncConfigTo(context.player())));
        ServerPlayNetworking.registerGlobalReceiver(InjuredBleedingPackets.UpdateConfigC2S.ID, (packet, context) ->
                context.server().execute(() -> updateConfig(context.player(), packet.json())));
    }

    public static void handleDamage(LivingEntity entity, float amount) {
        if (amount > 0.0F && entity.getWorld() instanceof ServerWorld world && entity.getMaxHealth() > 0.0F) {
            spawnBloodEffect(world, entity, amount);
        }
    }

    private static void spawnBloodEffect(ServerWorld world, LivingEntity entity, float amount) {
        float damagePercent = amount / entity.getMaxHealth();
        Scale scale = Scale.fromDamagePercent(damagePercent);
        Random random = new Random(world.getTime() * 31L + entity.getId() * 131L + Float.floatToIntBits(amount));
        Vec3d origin = entity.getPos();
        Vec3d emitter = attackPartPos(entity, random);
        Vec3d emitterOffset = emitter.subtract(origin);
        int sprayTicks = InjuredBleedingConfig.getInstance().sprayTicks;

        List<InjuredBleedingPackets.BloodDropData> drops = new ArrayList<>(scale.dropCount());
        for (int i = 0; i < scale.dropCount(); i++) {
            SurfaceHit landing = randomGroundLanding(world, entity, emitter, random);
            int flightTicks = estimateFlightTicks(emitter, landing.pos(), random);
            drops.add(new InjuredBleedingPackets.BloodDropData(
                    landing.pos().x,
                    landing.pos().y,
                    landing.pos().z,
                    (float) landing.normal().x,
                    (float) landing.normal().y,
                    (float) landing.normal().z,
                    random.nextFloat() * 360.0F,
                    sprayTicks <= 1 ? 0 : Math.round(i * sprayTicks / (float) Math.max(1, scale.dropCount() - 1)),
                    flightTicks,
                    random.nextInt()
            ));
        }

        InjuredBleedingPackets.BloodEffectS2C packet = new InjuredBleedingPackets.BloodEffectS2C(
                entity.getId(),
                origin.x,
                entity.getY(),
                origin.z,
                emitterOffset.x,
                emitterOffset.y,
                emitterOffset.z,
                scale.networkId(),
                DEFAULT_FADE_TICKS,
                drops
        );
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.squaredDistanceTo(entity) <= SYNC_DISTANCE_SQUARED) {
                ServerPlayNetworking.send(player, packet);
            }
        }
    }

    private static SurfaceHit randomGroundLanding(ServerWorld world, LivingEntity entity, Vec3d emitter, Random random) {
        Vec3d origin = entity.getPos();
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double radius = Math.sqrt(random.nextDouble()) * LANDING_RADIUS;
            double x = origin.x + Math.cos(angle) * radius;
            double z = origin.z + Math.sin(angle) * radius;
            Vec3d top = new Vec3d(x, entity.getY() + entity.getHeight() + 2.0D, z);
            Vec3d bottom = new Vec3d(x, entity.getY() - 4.0D, z);
            HitResult hit = world.raycast(new RaycastContext(
                    top,
                    bottom,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    entity
            ));
            if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK && blockHit.getSide() == Direction.UP) {
                Vec3d pos = blockHit.getPos().add(0.0D, 0.006D, 0.0D);
                if (pos.y <= emitter.y) {
                    return new SurfaceHit(pos, new Vec3d(0.0D, 1.0D, 0.0D));
                }
            }
        }
        return new SurfaceHit(new Vec3d(origin.x, Math.min(entity.getY() + 0.006D, emitter.y), origin.z), new Vec3d(0.0D, 1.0D, 0.0D));
    }

    private static int estimateFlightTicks(Vec3d start, Vec3d end, Random random) {
        double horizontal = Math.hypot(end.x - start.x, end.z - start.z);
        if (horizontal < 0.02D) {
            return randomRangeInt(random, 6, 12);
        }
        double pitch = -Math.toRadians(randomRange(random, 0.0D, 90.0D));
        double verticalDelta = end.y - start.y;
        double numerator = 2.0D * (verticalDelta - horizontal * Math.tan(pitch));
        double ticksSquared = numerator / BALLISTIC_GRAVITY_PER_TICK;
        if (!Double.isFinite(ticksSquared) || ticksSquared <= 0.0D) {
            pitch = -Math.toRadians(randomRange(random, 30.0D, 90.0D));
            numerator = 2.0D * (verticalDelta - horizontal * Math.tan(pitch));
            ticksSquared = numerator / BALLISTIC_GRAVITY_PER_TICK;
        }
        if (!Double.isFinite(ticksSquared) || ticksSquared <= 0.0D) {
            return randomRangeInt(random, 6, 16);
        }
        return Math.clamp((int) Math.round(Math.sqrt(ticksSquared)), 4, 18);
    }

    private static Vec3d attackPartPos(LivingEntity entity, Random random) {
        double height = entity.getHeight();
        BodyPart part = BodyPart.random(random);
        double yRatio = switch (part) {
            case HEAD -> 0.88D;
            case CHEST -> 0.58D;
            case LEGS -> 0.32D;
            case FEET -> 0.08D;
        };
        double sideAngle = random.nextDouble() * Math.PI * 2.0D;
        double sideRadius = Math.min(0.25D, Math.max(0.08D, entity.getWidth() * 0.35D));
        return new Vec3d(
                entity.getX() + Math.cos(sideAngle) * sideRadius,
                entity.getY() + height * yRatio,
                entity.getZ() + Math.sin(sideAngle) * sideRadius
        );
    }

    private static double randomRange(Random random, double min, double max) {
        return min + (max - min) * random.nextDouble();
    }

    private static int randomRangeInt(Random random, int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    private enum BodyPart {
        HEAD,
        CHEST,
        LEGS,
        FEET;

        private static BodyPart random(Random random) {
            BodyPart[] values = values();
            return values[random.nextInt(values.length)];
        }
    }

    public static void syncConfigTo(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, new InjuredBleedingPackets.ConfigS2C(InjuredBleedingConfig.getInstance().toJson()));
    }

    private static void updateConfig(ServerPlayerEntity player, String json) {
        if (!player.hasPermissionLevel(2) && !player.isCreative()) {
            player.sendMessage(Text.literal("\u00a7cNo permission to update injured bleeding config"), true);
            return;
        }
        InjuredBleedingConfig config = InjuredBleedingConfig.fromJson(json);
        InjuredBleedingConfig.setInstance(config);
        InjuredBleedingPackets.ConfigS2C sync = new InjuredBleedingPackets.ConfigS2C(config.toJson());
        for (ServerPlayerEntity target : player.getServer().getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(target, sync);
        }
        player.sendMessage(Text.literal("\u00a7aInjured bleeding config updated"), true);
    }

    private record SurfaceHit(Vec3d pos, Vec3d normal) {
    }

    private record Scale(int networkId, int dropCount) {
        private static Scale fromDamagePercent(float damagePercent) {
            if (damagePercent < 0.05F) {
                return new Scale(0, 8);
            }
            if (damagePercent < 0.15F) {
                return new Scale(1, 16);
            }
            if (damagePercent < 0.30F) {
                return new Scale(2, 28);
            }
            return new Scale(3, 42);
        }
    }
}
