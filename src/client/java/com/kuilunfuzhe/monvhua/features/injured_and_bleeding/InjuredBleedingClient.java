package com.kuilunfuzhe.monvhua.features.injured_and_bleeding;

import com.kuilunfuzhe.monvhua.features.paint.PaintRenderLayers;
import com.kuilunfuzhe.monvhua.gui.CombinedConfigScreen;
import com.kuilunfuzhe.monvhua.item.config.InjuredBleedingConfig;
import com.kuilunfuzhe.monvhua.network.injured_and_bleeding.InjuredBleedingPackets;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public final class InjuredBleedingClient {
    private static final double MAX_RENDER_DISTANCE_SQUARED = 96.0D * 96.0D;
    private static final double BALLISTIC_GRAVITY_PER_TICK = -0.04D;
    private static final float DECAL_Y_OFFSET = 0.004F;
    private static final float PIXEL_SIZE = 1.0F / 16.0F;
    private static final List<BloodDecal> DECALS = new ArrayList<>();
    private static final List<SprayDrop> SPRAY_DROPS = new ArrayList<>();

    private InjuredBleedingClient() {
    }

    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(InjuredBleedingPackets.BloodEffectS2C.ID, (packet, context) ->
                context.client().execute(() -> applyEffect(packet)));
        ClientPlayNetworking.registerGlobalReceiver(InjuredBleedingPackets.ConfigS2C.ID, (packet, context) ->
                context.client().execute(() -> {
                    InjuredBleedingConfig config = InjuredBleedingConfig.fromJson(packet.json());
                    InjuredBleedingConfig.syncInstance(config);
                    if (context.client().currentScreen instanceof CombinedConfigScreen screen) {
                        screen.receiveInjuredBleedingConfig(config);
                    }
                }));
        ClientTickEvents.END_CLIENT_TICK.register(InjuredBleedingClient::tick);
    }

    private static void applyEffect(InjuredBleedingPackets.BloodEffectS2C packet) {
        Vec3d origin = packet.origin();
        Vec3d emitterOffset = packet.emitterOffset();
        int fadeTicks = Math.max(20, packet.fadeTicks());
        for (InjuredBleedingPackets.BloodDropData drop : packet.drops()) {
            SPRAY_DROPS.add(SprayDrop.create(
                    packet.entityId(),
                    origin,
                    emitterOffset,
                    drop.end(),
                    drop.normal().normalize(),
                    drop.rotationDegrees(),
                    drop.delayTicks(),
                    Math.max(4, drop.flightTicks()),
                    drop.shapeSeed(),
                    fadeTicks
            ));
        }
    }

    private static void tick(MinecraftClient client) {
        if (client.world == null) {
            DECALS.clear();
            SPRAY_DROPS.clear();
            return;
        }
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
        DECALS.add(new BloodDecal(pos, normal, rotationDegrees, shapeSeed, fadeTicks));
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
        private final Vec3d packetEnd;
        private final Vec3d packetNormal;
        private final float rotationDegrees;
        private final int delayTicks;
        private int flightTicks;
        private final int shapeSeed;
        private final int fadeTicks;
        private Vec3d start;
        private Vec3d end;
        private Vec3d normal;
        private Vec3d velocity;
        private int age;
        private boolean launched;

        private SprayDrop(int entityId, Vec3d origin, Vec3d emitterOffset, Vec3d packetEnd, Vec3d packetNormal, float rotationDegrees,
                          int delayTicks, int flightTicks, int shapeSeed, int fadeTicks) {
            this.entityId = entityId;
            this.origin = origin;
            this.emitterOffset = emitterOffset;
            this.packetEnd = packetEnd;
            this.packetNormal = packetNormal;
            this.rotationDegrees = rotationDegrees;
            this.delayTicks = delayTicks;
            this.flightTicks = flightTicks;
            this.shapeSeed = shapeSeed;
            this.fadeTicks = fadeTicks;
        }

        private static SprayDrop create(int entityId, Vec3d origin, Vec3d emitterOffset, Vec3d packetEnd, Vec3d packetNormal,
                                        float rotationDegrees, int delayTicks, int flightTicks, int shapeSeed, int fadeTicks) {
            return new SprayDrop(entityId, origin, emitterOffset, packetEnd, packetNormal, rotationDegrees, delayTicks, flightTicks, shapeSeed, fadeTicks);
        }

        private boolean tickAndExpired(MinecraftClient client) {
            age++;
            if (age <= delayTicks) {
                return false;
            }
            if (!launched && !launch(client)) {
                return true;
            }
            int flightAge = age - delayTicks;
            if (flightAge >= flightTicks) {
                spawn(client, end, 0.0D, 0.0D, 0.0D);
                addImpactDecal(end, normal, rotationDegrees, shapeSeed, fadeTicks);
                return true;
            }
            double progress = flightAge / (double) flightTicks;
            Vec3d pos = ballisticPos(flightAge);
            Vec3d particleVelocity = velocity.add(0.0D, BALLISTIC_GRAVITY_PER_TICK * flightAge, 0.0D);
            spawn(client, pos, particleVelocity.x, particleVelocity.y, particleVelocity.z);
            return false;
        }

        private boolean launch(MinecraftClient client) {
            Entity entity = client.world == null ? null : client.world.getEntityById(entityId);
            Vec3d currentOrigin = entity == null ? origin : entity.getPos();
            start = currentOrigin.add(emitterOffset);
            end = packetEnd;
            normal = packetNormal.lengthSquared() < 1.0E-6D ? new Vec3d(0.0D, 1.0D, 0.0D) : packetNormal.normalize();
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

        private Vec3d ballisticPos(int flightAge) {
            double ticks = Math.min(flightAge, flightTicks);
            return start
                    .add(velocity.multiply(ticks))
                    .add(0.0D, 0.5D * BALLISTIC_GRAVITY_PER_TICK * ticks * ticks, 0.0D);
        }

        private static void spawn(MinecraftClient client, Vec3d pos, double vx, double vy, double vz) {
            if (client.particleManager != null) {
                client.particleManager.addParticle(ParticleTypes.DRIPPING_DRIPSTONE_LAVA, pos.x, pos.y, pos.z, vx, vy, vz);
            }
        }
    }

    private record Pixel(float x, float y, float size) {
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
