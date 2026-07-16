package com.kuilunfuzhe.monvhua.features.injured_and_bleeding;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.kuilunfuzhe.monvhua.features.paint.PaintRenderLayers;
import com.kuilunfuzhe.monvhua.gui.CombinedConfigScreen;
import com.kuilunfuzhe.monvhua.item.config.InjuredBleedingConfig;
import com.kuilunfuzhe.monvhua.network.injured_and_bleeding.InjuredBleedingPackets;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public final class InjuredBleedingClient {
    private static final double MAX_RENDER_DISTANCE_SQUARED = 96.0D * 96.0D;
    private static final double BALLISTIC_GRAVITY_PER_TICK = -0.04D;
    private static final float DECAL_Y_OFFSET = 0.004F;
    private static final float PIXEL_SIZE = 1.0F / 16.0F;
    private static final float BLOOD_BUTTERFLY_SCALE = 1.0F;
    private static final double BLOOD_BUTTERFLY_BASE_LIFETIME_SECONDS = 8.0D;
    private static final int MAX_ACTIVE_DECALS = 1536;
    private static final int MAX_ACTIVE_SPRAY_DROPS = 4096;
    private static final int MAX_TRAIL_PARTICLE_SPAWNS_PER_TICK = 96;
    private static final int MAX_TRAIL_PARTICLES_PER_DROP = 2;
    private static final List<BloodDecal> DECALS = new ArrayList<>();
    private static final List<SprayDrop> SPRAY_DROPS = new ArrayList<>();
    private static Constructor<?> bloodButterflyOptionsConstructor;
    private static boolean bloodButterflyOptionsUnavailable;
    private static int trailParticleSpawnBudget;

    private InjuredBleedingClient() {
    }

    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(InjuredBleedingPackets.BloodEffectS2C.ID, (packet, context) ->
                context.client().execute(() -> applyEffect(packet)));
        ClientPlayNetworking.registerGlobalReceiver(InjuredBleedingPackets.ConfigS2C.ID, (packet, context) ->
                context.client().execute(() -> {
                    InjuredBleedingConfig config = InjuredBleedingConfig.fromJson(packet.json());
                    InjuredBleedingConfig.syncInstance(config);
                    CombinedConfigScreen.receiveInjuredBleedingConfig(config);
                }));
        ClientTickEvents.END_CLIENT_TICK.register(InjuredBleedingClient::tick);
    }

    private static void applyEffect(InjuredBleedingPackets.BloodEffectS2C packet) {
        if (isSourceEntityGone(MinecraftClient.getInstance(), packet.entityId())) {
            return;
        }
        Vec3d origin = packet.origin();
        Vec3d emitterOffset = packet.emitterOffset();
        int fadeTicks = Math.max(20, packet.fadeTicks());
        for (InjuredBleedingPackets.BloodDropData drop : packet.drops()) {
            addSprayDrop(SprayDrop.create(
                    packet.entityId(),
                    origin,
                    emitterOffset,
                    drop.angleRadians(),
                    drop.radius(),
                    drop.rotationDegrees(),
                    drop.delayTicks(),
                    Math.max(4, drop.flightTicks()),
                    drop.shapeSeed(),
                    fadeTicks,
                    Math.clamp(packet.butterflyChancePercent(), 0.0D, 100.0D),
                    Math.clamp(packet.butterflyLifetimeSeconds(), 0.1D, 32.0D)
            ));
        }
    }

    private static boolean isSourceEntityGone(MinecraftClient client, int entityId) {
        if (client.world == null) {
            return true;
        }
        Entity entity = client.world.getEntityById(entityId);
        return entity == null || !entity.isAlive() || entity.isRemoved();
    }

    private static void tick(MinecraftClient client) {
        if (client.world == null) {
            DECALS.clear();
            SPRAY_DROPS.clear();
            return;
        }
        trailParticleSpawnBudget = MAX_TRAIL_PARTICLE_SPAWNS_PER_TICK;
        DECALS.removeIf(BloodDecal::tickAndExpired);
        Iterator<SprayDrop> drops = SPRAY_DROPS.iterator();
        while (drops.hasNext()) {
            SprayDrop drop = drops.next();
            if (drop.tickAndExpired(client)) {
                drops.remove();
            }
        }
    }

    public static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || DECALS.isEmpty() || context.consumers() == null || context.matrixStack() == null) {
            return;
        }
        Vec3d camera = context.camera().getPos();
        Matrix4f matrix = context.matrixStack().peek().getPositionMatrix();
        VertexConsumer vertices = context.consumers().getBuffer(PaintRenderLayers.paintOverlay());
        for (BloodDecal decal : DECALS) {
            if (decal.pos.squaredDistanceTo(camera) > MAX_RENDER_DISTANCE_SQUARED) {
                continue;
            }
            drawDecal(vertices, matrix, camera, decal);
        }
    }

    private static void drawDecal(VertexConsumer vertices, Matrix4f matrix, Vec3d camera, BloodDecal decal) {
        int color = decal.color();
        int alpha = (color >>> 24) & 0xFF;
        if (alpha <= 0) {
            return;
        }
        Vec3d right = decal.right;
        Vec3d up = decal.up;
        Vec3d normal = decal.normal;
        for (Pixel pixel : decal.pixels) {
            float half = pixel.size * 0.5F;
            emitPixelVertex(vertices, matrix, camera, decal.pos, right, up, normal, pixel.x - half, pixel.y - half, color);
            emitPixelVertex(vertices, matrix, camera, decal.pos, right, up, normal, pixel.x + half, pixel.y - half, color);
            emitPixelVertex(vertices, matrix, camera, decal.pos, right, up, normal, pixel.x + half, pixel.y + half, color);
            emitPixelVertex(vertices, matrix, camera, decal.pos, right, up, normal, pixel.x - half, pixel.y + half, color);
        }
    }

    private static void emitPixelVertex(VertexConsumer vertices, Matrix4f matrix, Vec3d camera, Vec3d center,
                                        Vec3d right, Vec3d up, Vec3d normal, float x, float y, int color) {
        Vec3d pos = center
                .add(right.multiply(x))
                .add(up.multiply(y))
                .add(normal.multiply(DECAL_Y_OFFSET));
        vertices.vertex(matrix, (float) (pos.x - camera.x), (float) (pos.y - camera.y), (float) (pos.z - camera.z))
                .color((color >>> 16) & 0xFF, (color >>> 8) & 0xFF, color & 0xFF, (color >>> 24) & 0xFF)
                .texture(0.0F, 0.0F)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal((float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static void addImpactDecal(Vec3d pos, Vec3d normal, float rotationDegrees, int shapeSeed, int fadeTicks) {
        trimOldest(DECALS, MAX_ACTIVE_DECALS - 1);
        DECALS.add(new BloodDecal(pos, normal, rotationDegrees, shapeSeed, fadeTicks));
    }

    private static void addSprayDrop(SprayDrop drop) {
        trimOldest(SPRAY_DROPS, MAX_ACTIVE_SPRAY_DROPS - 1);
        SPRAY_DROPS.add(drop);
    }

    private static <T> void trimOldest(List<T> list, int maxSize) {
        int removeCount = list.size() - Math.max(0, maxSize);
        if (removeCount > 0) {
            list.subList(0, removeCount).clear();
        }
    }

    private static boolean addBloodButterflyParticle(MinecraftClient client, Vec3d pos, int seed, double lifetimeSeconds) {
        if (client.particleManager == null) {
            return false;
        }
        ParticleEffect effect = createBloodButterflyEffect(lifetimeSeconds);
        if (effect == null) {
            return false;
        }
        Vec3d velocity = randomButterflyVelocity(seed);
        return client.particleManager.addParticle(effect, pos.x, pos.y, pos.z, velocity.x, velocity.y, velocity.z) != null;
    }

    private static ParticleEffect createBloodButterflyEffect(double lifetimeSeconds) {
        try {
            Constructor<?> constructor = bloodButterflyOptionsConstructor;
            if (constructor == null) {
                if (bloodButterflyOptionsUnavailable) {
                    return null;
                }
                Class<?> optionsClass = Class.forName("com.example.manosaba_ice_expand.particle.BloodButterflyParticleOptions");
                constructor = optionsClass.getConstructor(float.class, float.class);
                bloodButterflyOptionsConstructor = constructor;
            }
            float lifeScale = (float) Math.clamp(lifetimeSeconds / BLOOD_BUTTERFLY_BASE_LIFETIME_SECONDS, 0.01D, 4.0D);
            Object effect = constructor.newInstance(BLOOD_BUTTERFLY_SCALE, lifeScale);
            return effect instanceof ParticleEffect particleEffect ? particleEffect : null;
        } catch (ReflectiveOperationException | LinkageError e) {
            bloodButterflyOptionsUnavailable = true;
            MonvhuaMod.LOGGER.warn("[Monvhua] Failed to create manosaba blood butterfly particle options", e);
            return null;
        }
    }

    private static Vec3d randomButterflyVelocity(int seed) {
        Random random = new Random(seed ^ 0x6B8B4567L);
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double horizontalSpeed = 0.035D + random.nextDouble() * 0.075D;
        double upwardSpeed = 0.005D + random.nextDouble() * 0.035D;
        return new Vec3d(
                Math.cos(angle) * horizontalSpeed,
                upwardSpeed,
                Math.sin(angle) * horizontalSpeed
        );
    }

    private static final class BloodDecal {
        private final Vec3d pos;
        private final Vec3d normal;
        private final Vec3d right;
        private final Vec3d up;
        private final List<Pixel> pixels;
        private final int fadeTicks;
        private int age;

        private BloodDecal(Vec3d pos, Vec3d normal, float rotationDegrees, int shapeSeed, int fadeTicks) {
            this.pos = pos;
            this.normal = normal.normalize();
            Basis basis = Basis.fromNormal(this.normal, rotationDegrees);
            this.right = basis.right();
            this.up = basis.up();
            this.pixels = createPixels(shapeSeed);
            this.fadeTicks = fadeTicks;
        }

        private boolean tickAndExpired() {
            age++;
            return age >= fadeTicks;
        }

        private int color() {
            float progress = MathHelper.clamp(age / (float) Math.max(1, fadeTicks), 0.0F, 1.0F);
            int alpha = MathHelper.clamp(Math.round(255.0F * (1.0F - progress)), 0, 255);
            int red;
            if (progress < 0.5F) {
                red = MathHelper.lerp(progress / 0.5F, 255, 96);
            } else {
                red = MathHelper.lerp((progress - 0.5F) / 0.5F, 96, 0);
            }
            return (alpha << 24) | (MathHelper.clamp(red, 0, 255) << 16);
        }

        private static List<Pixel> createPixels(int seed) {
            Random random = new Random(seed);
            List<Pixel> pixels = new ArrayList<>(9);
            for (int py = -1; py <= 1; py++) {
                for (int px = -1; px <= 1; px++) {
                    double distance = Math.sqrt(px * px + py * py);
                    if (distance > 0.0D && random.nextFloat() < 0.18F + 0.12F * distance) {
                        continue;
                    }
                    float jitterX = (random.nextFloat() - 0.5F) * PIXEL_SIZE * 0.35F;
                    float jitterY = (random.nextFloat() - 0.5F) * PIXEL_SIZE * 0.35F;
                    float size = PIXEL_SIZE * (0.72F + random.nextFloat() * 0.42F);
                    pixels.add(new Pixel(px * PIXEL_SIZE + jitterX, py * PIXEL_SIZE + jitterY, size));
                }
            }
            if (pixels.isEmpty()) {
                pixels.add(new Pixel(0.0F, 0.0F, PIXEL_SIZE));
            }
            return pixels;
        }
    }

    private static final class SprayDrop {
        private final int entityId;
        private final Vec3d origin;
        private final Vec3d emitterOffset;
        private final float angleRadians;
        private final float radius;
        private final float rotationDegrees;
        private final int delayTicks;
        private int flightTicks;
        private final int shapeSeed;
        private final int fadeTicks;
        private final double butterflyChancePercent;
        private final double butterflyLifetimeSeconds;
        private final boolean replaceWithButterfly;
        private Vec3d start;
        private Vec3d end;
        private Vec3d normal;
        private Vec3d velocity;
        private int age;
        private boolean launched;
        private final List<Particle> trailParticles = new ArrayList<>(MAX_TRAIL_PARTICLES_PER_DROP);

        private SprayDrop(int entityId, Vec3d origin, Vec3d emitterOffset, float angleRadians, float radius, float rotationDegrees,
                          int delayTicks, int flightTicks, int shapeSeed, int fadeTicks, double butterflyChancePercent,
                          double butterflyLifetimeSeconds) {
            this.entityId = entityId;
            this.origin = origin;
            this.emitterOffset = emitterOffset;
            this.angleRadians = angleRadians;
            this.radius = radius;
            this.rotationDegrees = rotationDegrees;
            this.delayTicks = delayTicks;
            this.flightTicks = flightTicks;
            this.shapeSeed = shapeSeed;
            this.fadeTicks = fadeTicks;
            this.butterflyChancePercent = butterflyChancePercent;
            this.butterflyLifetimeSeconds = butterflyLifetimeSeconds;
            this.replaceWithButterfly = shouldReplaceDecalWithButterfly(shapeSeed, butterflyChancePercent);
        }

        private static SprayDrop create(int entityId, Vec3d origin, Vec3d emitterOffset, float angleRadians, float radius,
                                        float rotationDegrees, int delayTicks, int flightTicks, int shapeSeed, int fadeTicks,
                                        double butterflyChancePercent, double butterflyLifetimeSeconds) {
            return new SprayDrop(entityId, origin, emitterOffset, angleRadians, radius, rotationDegrees, delayTicks, flightTicks, shapeSeed, fadeTicks, butterflyChancePercent, butterflyLifetimeSeconds);
        }

        private boolean tickAndExpired(MinecraftClient client) {
            if (isSourceEntityGone(client, entityId)) {
                clearTrailParticles();
                return true;
            }
            age++;
            if (age <= delayTicks) {
                return false;
            }
            if (!launched && !launch(client)) {
                return true;
            }
            int flightAge = age - delayTicks;
            if (flightAge >= flightTicks) {
                if (!replaceWithButterfly) {
                    spawn(client, end, 0.0D, 0.0D, 0.0D);
                    addImpactDecal(end, normal, rotationDegrees, shapeSeed, fadeTicks);
                } else if (!addBloodButterflyParticle(client, end, shapeSeed, butterflyLifetimeSeconds)) {
                    addImpactDecal(end, normal, rotationDegrees, shapeSeed, fadeTicks);
                }
                return true;
            }
            if (replaceWithButterfly) {
                return false;
            }
            if (!consumeTrailParticleSpawnBudget()) {
                return false;
            }
            Vec3d pos = ballisticPos(flightAge);
            Vec3d particleVelocity = velocity.add(0.0D, BALLISTIC_GRAVITY_PER_TICK * flightAge, 0.0D);
            trackTrailParticle(spawn(client, pos, particleVelocity.x, particleVelocity.y, particleVelocity.z));
            return false;
        }

        private boolean launch(MinecraftClient client) {
            Entity entity = client.world == null ? null : client.world.getEntityById(entityId);
            Vec3d currentOrigin = entity == null ? origin : entity.getPos();
            start = currentOrigin.add(emitterOffset);
            SurfaceHit landing = randomGroundLanding(client, entity, currentOrigin, start);
            end = landing.pos();
            normal = landing.normal();
            double ticks = Math.max(1.0D, flightTicks);
            if (end.y < start.y) {
                double maxDownwardTicks = Math.sqrt(Math.max(0.0D, 2.0D * (end.y - start.y) / BALLISTIC_GRAVITY_PER_TICK));
                if (Double.isFinite(maxDownwardTicks) && maxDownwardTicks > 1.0D && ticks > maxDownwardTicks) {
                    ticks = maxDownwardTicks;
                    flightTicks = Math.max(1, (int) Math.floor(ticks));
                    ticks = flightTicks;
                }
            }
            velocity = new Vec3d(
                    (end.x - start.x) / ticks,
                    Math.min(0.0D, (end.y - start.y - 0.5D * BALLISTIC_GRAVITY_PER_TICK * ticks * ticks) / ticks),
                    (end.z - start.z) / ticks
            );
            launched = true;
            return true;
        }

        private SurfaceHit randomGroundLanding(MinecraftClient client, Entity entity, Vec3d currentOrigin, Vec3d emitter) {
            if (client.world != null) {
                Entity raycastEntity = entity == null ? client.player : entity;
                if (raycastEntity == null) {
                    return new SurfaceHit(new Vec3d(currentOrigin.x, Math.min(currentOrigin.y + 0.006D, emitter.y), currentOrigin.z), new Vec3d(0.0D, 1.0D, 0.0D));
                }
                double x = currentOrigin.x + Math.cos(angleRadians) * radius;
                double z = currentOrigin.z + Math.sin(angleRadians) * radius;
                double height = entity == null ? 2.0D : entity.getHeight();
                Vec3d top = new Vec3d(x, currentOrigin.y + height + 2.0D, z);
                Vec3d bottom = new Vec3d(x, currentOrigin.y - 4.0D, z);
                HitResult hit = client.world.raycast(new RaycastContext(
                        top,
                        bottom,
                        RaycastContext.ShapeType.COLLIDER,
                        RaycastContext.FluidHandling.NONE,
                        raycastEntity
                ));
                if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK && blockHit.getSide() == Direction.UP) {
                    Vec3d pos = blockHit.getPos().add(0.0D, 0.006D, 0.0D);
                    if (pos.y <= emitter.y) {
                        return new SurfaceHit(pos, new Vec3d(0.0D, 1.0D, 0.0D));
                    }
                }
            }
            return new SurfaceHit(new Vec3d(currentOrigin.x, Math.min(currentOrigin.y + 0.006D, emitter.y), currentOrigin.z), new Vec3d(0.0D, 1.0D, 0.0D));
        }

        private Vec3d ballisticPos(int flightAge) {
            double ticks = Math.min(flightAge, flightTicks);
            return start
                    .add(velocity.multiply(ticks))
                    .add(0.0D, 0.5D * BALLISTIC_GRAVITY_PER_TICK * ticks * ticks, 0.0D);
        }

        private static boolean shouldReplaceDecalWithButterfly(int shapeSeed, double butterflyChancePercent) {
            double chance = Math.clamp(butterflyChancePercent, 0.0D, 100.0D);
            if (chance <= 0.0D) {
                return false;
            }
            if (chance >= 100.0D) {
                return true;
            }
            return Math.floorMod(shapeSeed, 10_000) < chance * 100.0D;
        }

        private void trackTrailParticle(Particle particle) {
            if (particle == null) {
                return;
            }
            trailParticles.add(particle);
            while (trailParticles.size() > MAX_TRAIL_PARTICLES_PER_DROP) {
                trailParticles.remove(0).markDead();
            }
        }

        private void clearTrailParticles() {
            for (Particle particle : trailParticles) {
                particle.markDead();
            }
            trailParticles.clear();
        }

        private static Particle spawn(MinecraftClient client, Vec3d pos, double vx, double vy, double vz) {
            if (client.particleManager != null) {
                return client.particleManager.addParticle(ParticleTypes.FALLING_LAVA, pos.x, pos.y, pos.z, vx, vy, vz);
            }
            return null;
        }
    }

    private record Pixel(float x, float y, float size) {
    }

    private static boolean consumeTrailParticleSpawnBudget() {
        if (trailParticleSpawnBudget <= 0) {
            return false;
        }
        trailParticleSpawnBudget--;
        return true;
    }

    private record Basis(Vec3d right, Vec3d up) {
        private static Basis fromNormal(Vec3d normal, float rotationDegrees) {
            Vec3d reference = Math.abs(normal.y) > 0.9D ? new Vec3d(1.0D, 0.0D, 0.0D) : new Vec3d(0.0D, 1.0D, 0.0D);
            Vec3d right = reference.crossProduct(normal).normalize();
            Vec3d up = normal.crossProduct(right).normalize();
            double angle = Math.toRadians(rotationDegrees);
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            Vec3d rotatedRight = right.multiply(cos).add(up.multiply(sin)).normalize();
            Vec3d rotatedUp = normal.crossProduct(rotatedRight).normalize();
            return new Basis(rotatedRight, rotatedUp);
        }
    }

    private record SurfaceHit(Vec3d pos, Vec3d normal) {
    }
}
