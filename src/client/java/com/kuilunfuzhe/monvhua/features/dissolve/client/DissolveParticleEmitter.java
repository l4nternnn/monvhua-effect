package com.kuilunfuzhe.monvhua.features.dissolve.client;

import com.kuilunfuzhe.monvhua.features.dissolve.DissolveProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;

final class DissolveParticleEmitter {
    private static final double MIN_BODY = 0.02D;
    private static final double MAX_BODY = 0.98D;
    private static final double EPSILON = 1.0E-6D;

    private DissolveParticleEmitter() {
    }

    static void tick(MinecraftClient client, PlayerEntity player, DissolveClientController.ClientDissolveState state) {
        DissolveProfile profile = state.profile();
        if (profile.particlesPerTick() <= 0 || client.world == null) {
            return;
        }
        int particleElapsedTicks = state.elapsedTicks()
                - profile.particleDelayTicks()
                - Math.round(profile.durationTicks() * profile.particleTrailRatio());
        if (particleElapsedTicks <= 0 || particleElapsedTicks >= profile.durationTicks()) {
            return;
        }
        DissolveMaskGenerator.Sweep sweep = DissolveMaskGenerator.sweep(particleElapsedTicks, profile);
        BodySegment segment = segmentForSweep(sweep);
        if (segment == null) {
            return;
        }
        double yaw = Math.toRadians(player.bodyYaw);
        Vec3d left = new Vec3d(-Math.cos(yaw), 0.0D, -Math.sin(yaw));
        Vec3d front = new Vec3d(-Math.sin(yaw), 0.0D, Math.cos(yaw));
        double width = Math.max(0.3D, player.getWidth());
        double height = Math.max(1.0D, player.getHeight());

        for (int i = 0; i < profile.particlesPerTick(); i++) {
            BodyPoint sample = sampleBodyPoint(client, sweep, profile, segment);
            if (sample == null) {
                continue;
            }
            double bodyX = sample.x();
            double bodyY = sample.y();
            double localX = (bodyX - 0.5D) * width * 1.8D;
            double localY = (1.0D - bodyY) * height;
            Vec3d pos = player.getPos()
                    .add(0.0D, localY, 0.0D)
                    .add(left.multiply(localX))
                    .add(front.multiply(width * 0.35D));
            Vec3d random = new Vec3d(
                    client.world.random.nextDouble() - 0.5D,
                    client.world.random.nextDouble() - 0.5D,
                    client.world.random.nextDouble() - 0.5D
            ).multiply(profile.particleRandomSpeed());
            Vec3d velocity = left.multiply(-profile.particleLeftSpeed())
                    .add(0.0D, profile.particleUpSpeed(), 0.0D)
                    .add(random);
            client.particleManager.addParticle(
                    ParticleTypes.POOF,
                    pos.x,
                    pos.y,
                    pos.z,
                    velocity.x,
                    velocity.y,
                    velocity.z
            );
        }
    }

    private static BodyPoint sampleBodyPoint(MinecraftClient client, DissolveMaskGenerator.Sweep sweep,
                                             DissolveProfile profile, BodySegment segment) {
        double t = client.world.random.nextDouble();
        double edgeJitter = (client.world.random.nextDouble() - 0.5D) * profile.edgeBand() * 2.0D;
        double sampledX = lerp(segment.a().x(), segment.b().x(), t) + sweep.dirX() * edgeJitter;
        double sampledY = lerp(segment.a().y(), segment.b().y(), t) + sweep.dirY() * edgeJitter;
        if (!isInsideBody(sampledX, sampledY)) {
            return null;
        }
        return new BodyPoint(sampledX, sampledY);
    }

    private static BodySegment segmentForSweep(DissolveMaskGenerator.Sweep sweep) {
        BodyPoint[] points = new BodyPoint[4];
        int count = 0;
        count = addIntersectionForX(sweep, MIN_BODY, points, count);
        count = addIntersectionForX(sweep, MAX_BODY, points, count);
        count = addIntersectionForY(sweep, MIN_BODY, points, count);
        count = addIntersectionForY(sweep, MAX_BODY, points, count);
        if (count < 2) {
            return null;
        }

        BodyPoint a = points[0];
        BodyPoint b = points[1];
        double maxDistanceSq = distanceSq(a, b);
        for (int i = 0; i < count; i++) {
            for (int j = i + 1; j < count; j++) {
                double distanceSq = distanceSq(points[i], points[j]);
                if (distanceSq > maxDistanceSq) {
                    maxDistanceSq = distanceSq;
                    a = points[i];
                    b = points[j];
                }
            }
        }
        if (maxDistanceSq <= EPSILON) {
            return null;
        }
        return new BodySegment(a, b);
    }

    private static int addIntersectionForX(DissolveMaskGenerator.Sweep sweep, double x,
                                           BodyPoint[] points, int count) {
        if (Math.abs(sweep.dirY()) <= EPSILON) {
            return count;
        }
        double y = sweep.originY() + (sweep.threshold() - (x - sweep.originX()) * sweep.dirX()) / sweep.dirY();
        if (y < MIN_BODY || y > MAX_BODY) {
            return count;
        }
        return addUniquePoint(points, count, new BodyPoint(x, y));
    }

    private static int addIntersectionForY(DissolveMaskGenerator.Sweep sweep, double y,
                                           BodyPoint[] points, int count) {
        if (Math.abs(sweep.dirX()) <= EPSILON) {
            return count;
        }
        double x = sweep.originX() + (sweep.threshold() - (y - sweep.originY()) * sweep.dirY()) / sweep.dirX();
        if (x < MIN_BODY || x > MAX_BODY) {
            return count;
        }
        return addUniquePoint(points, count, new BodyPoint(x, y));
    }

    private static int addUniquePoint(BodyPoint[] points, int count, BodyPoint point) {
        for (int i = 0; i < count; i++) {
            if (distanceSq(points[i], point) <= EPSILON) {
                return count;
            }
        }
        if (count >= points.length) {
            return count;
        }
        points[count] = point;
        return count + 1;
    }

    private static boolean isInsideBody(double x, double y) {
        return x >= MIN_BODY && x <= MAX_BODY && y >= MIN_BODY && y <= MAX_BODY;
    }

    private static double distanceSq(BodyPoint a, BodyPoint b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        return dx * dx + dy * dy;
    }

    private static double lerp(double from, double to, double progress) {
        return from + (to - from) * progress;
    }

    private record BodyPoint(double x, double y) {
    }

    private record BodySegment(BodyPoint a, BodyPoint b) {
    }
}
