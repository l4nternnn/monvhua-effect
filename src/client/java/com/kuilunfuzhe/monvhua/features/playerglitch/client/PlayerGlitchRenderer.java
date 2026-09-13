package com.kuilunfuzhe.monvhua.features.playerglitch.client;

import com.kuilunfuzhe.monvhua.network.playerglitch.PlayerGlitchPackets;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.UUID;

final class PlayerGlitchRenderer {
    private static final int MIN_ROWS = 48;
    private static final int MAX_ROWS = 64;
    private static final int MAX_TARGETS = 4;
    private static final int MAX_TOTAL_UNITS = 768;
    private static final double MAX_DISTANCE = 96.0D;
    private static final long ROW_SALT = 0xA24BAED4963EE407L;
    private static final long BLACK_LAYOUT_SALT = 0xC6BC279692B5CC83L;
    private static final long GHOST_SALT = 0xD1B54A32D192ED03L;
    private static final long SHIFT_SALT = 0x9E3779B97F4A7C15L;
    private static final long SEGMENT_SALT = 0x632BE59BD9B4E019L;

    private PlayerGlitchRenderer() {
    }

    static void render(WorldRenderContext context, Map<UUID, PlayerGlitchPackets.StateS2C> states) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null || states.isEmpty()) {
            return;
        }
        Camera camera = context.camera();
        Vec3d cameraPos = camera.getPos();
        Vec3d viewerPos = client.player.getPos().add(0.0D, client.player.getStandingEyeHeight(), 0.0D);
        VertexConsumer vertices = context.consumers().getBuffer(RenderLayer.getDebugQuads());
        Matrix4f matrix = context.matrixStack().peek().getPositionMatrix();
        int targets = 0;
        int remainingUnits = MAX_TOTAL_UNITS;
        long time = client.world.getTime();

        for (Map.Entry<UUID, PlayerGlitchPackets.StateS2C> entry : states.entrySet()) {
            if (targets >= MAX_TARGETS || remainingUnits <= 0) {
                break;
            }
            PlayerEntity target = client.world.getPlayerByUuid(entry.getKey());
            if (target == null || target == client.player || target.isSpectator()) {
                continue;
            }
            double distance = target.getPos().distanceTo(cameraPos);
            if (!Double.isFinite(distance) || distance < 0.25D || distance > MAX_DISTANCE) {
                continue;
            }
            int units = Math.min(remainingUnits, Math.clamp(entry.getValue().maxFragments(), 1, 256));
            renderTarget(vertices, matrix, cameraPos, viewerPos, target, entry.getValue(), time, units);
            remainingUnits -= units;
            targets++;
        }
    }

    private static void renderTarget(VertexConsumer out, Matrix4f matrix, Vec3d camera,
                                     Vec3d viewerPos, PlayerEntity target,
                                     PlayerGlitchPackets.StateS2C state, long time, int unitBudget) {
        Vec3d targetCenter = target.getPos().add(0.0D, target.getHeight() * 0.52D, 0.0D);
        Vec3d relative = viewerPos.subtract(targetCenter);
        Vec3d horizontal = new Vec3d(relative.x, 0.0D, relative.z);
        if (horizontal.lengthSquared() < 1.0E-6D) {
            horizontal = new Vec3d(0.0D, 0.0D, 1.0D);
        }
        Vec3d normal = horizontal.normalize();
        Vec3d right = new Vec3d(-normal.z, 0.0D, normal.x).normalize();
        Vec3d up = new Vec3d(0.0D, 1.0D, 0.0D);
        double frontOffset = Math.clamp(0.08D + target.getWidth() * 0.08D, 0.06D, 0.20D);
        // The matrix follows the front surface of a thin reference ellipsoid.
        // Each row receives its own center below; this avoids a flat decal stuck in the player.
        Vec3d surfaceOrigin = targetCenter.add(normal.multiply(frontOffset));

        double width = Math.clamp(target.getWidth() * 2.6D, 0.8D, 3.2D);
        double verticalDifference = viewerPos.y - targetCenter.y;
        double heightFactor = Math.clamp(1.0D + verticalDifference * 0.08D, 0.75D, 1.25D);
        double height = Math.clamp(target.getHeight() * 1.08D * heightFactor, 1.2D, 4.5D);
        long layoutCycle = Math.floorDiv(time, 3L);
        // Black geometry follows the current cycle immediately; only the row animation
        // itself is time-sliced, so the black layout is never one cycle behind.
        long layoutSeed = mix(state.seed() ^ layoutCycle ^ BLACK_LAYOUT_SALT);
        int rows = MIN_ROWS + (int) Math.floorMod(mix(layoutSeed), MAX_ROWS - MIN_ROWS + 1);
        double totalWeight = 0.0D;
        for (int row = 0; row < rows; row++) {
            totalWeight += rowWeight(layoutSeed, row);
        }

        double cursor = -height * 0.5D;
        int emitted = 0;
        for (int row = 0; row < rows && emitted < unitBudget; row++) {
            long rowHash = mix(layoutSeed ^ ROW_SALT * (row + 1L));
            double rowHeight = height * rowWeight(layoutSeed, row) / totalWeight;
            double y0 = cursor;
            double y1 = cursor + rowHeight;
            cursor = y1;
            double localY = ((y0 + y1) * 0.5D) / (height * 0.5D);
            double ellipseFactor = Math.sqrt(Math.max(0.0D, 1.0D - localY * localY));
            double rowHalfWidth = width * 0.5D * ellipseFactor;
            double rowMin = -rowHalfWidth;
            double rowMax = rowHalfWidth;
            double rowWidth = rowMax - rowMin;
            if (rowWidth < 0.02D) {
                continue;
            }

            double blackLength = rowWidth * (0.30D + unit(rowHash >>> 24) * 0.70D);
            double blackStart = rowMin + unit(rowHash >>> 40) * Math.max(0.0D, rowWidth - blackLength);
            double blackEnd = Math.min(rowMax, blackStart + blackLength);
            double rowShift = rowHorizontalShift(state, row, rowHash, time, rowWidth);
            double shiftedStart = Math.max(rowMin, Math.min(rowMax, blackStart + rowShift));
            double shiftedEnd = Math.max(rowMin, Math.min(rowMax, blackEnd + rowShift));
            double surfaceDepth = Math.max(0.0D, width * 0.24D) * ellipseFactor;
            Vec3d rowCenter = surfaceOrigin.add(normal.multiply(surfaceDepth));
            if (shiftedEnd - shiftedStart >= 0.001D) {
                emit(out, matrix, camera, rowCenter, right, up,
                        shiftedStart, shiftedEnd, y0, y1, 0x050609, 238);
                emitRowGhosts(out, matrix, camera, rowCenter, right, up,
                        shiftedStart, shiftedEnd, rowMin, rowMax, y0, y1,
                        rowHash, time, rowShift);
            }
            emitted++;
        }
    }

    private static double rowWeight(long seed, int row) {
        return 0.5D + unit(mix(seed ^ ROW_SALT * (row + 1L) ^ 0x51ED270B3F5A9C17L)) * 2.5D;
    }

    private static void emitRowGhosts(VertexConsumer out, Matrix4f matrix, Vec3d camera,
                                      Vec3d center, Vec3d right, Vec3d up,
                                      double blackStart, double blackEnd,
                                      double rowMin, double rowMax, double y0, double y1,
                                      long rowHash, long time, double rowShift) {
        if (Math.abs(rowShift) < 0.001D) {
            return;
        }
        int alpha = animatedAlpha(mix(rowHash ^ GHOST_SALT), time);
        if (alpha < 35) {
            return;
        }
        double length = Math.min((blackEnd - blackStart) * 0.10D, Math.abs(rowShift) * 0.75D);
        if (length < 0.001D) {
            return;
        }
        double gap = Math.max(0.012D, (rowMax - rowMin) * 0.006D);
        double leftStart = Math.max(rowMin, blackStart - gap - length);
        double leftEnd = Math.min(rowMax, blackStart - gap);
        double rightStart = Math.max(rowMin, blackEnd + gap);
        double rightEnd = Math.min(rowMax, blackEnd + gap + length);
        boolean redOnLeft = (rowHash & 1L) == 0L;
        if (rightEnd - rightStart >= 0.001D) {
            emit(out, matrix, camera, center, right, up, rightStart, rightEnd,
                    y0, y1, redOnLeft ? 0xFF304F : 0x3568FF, alpha);
        }
        if (leftEnd - leftStart >= 0.001D) {
            emit(out, matrix, camera, center, right, up, leftStart, leftEnd,
                    y0, y1, redOnLeft ? 0x3568FF : 0xFF304F, alpha);
        }
    }

    private static int animatedAlpha(long hash, long time) {
        long phase = Math.floorMod(time + hash, 24L);
        if (phase < 3L) {
            return (int) (45.0D + 45.0D * phase / 3.0D);
        }
        if (phase > 18L) {
            return (int) (45.0D + 45.0D * (24L - phase) / 6.0D);
        }
        return 90;
    }

    private static double rowHorizontalShift(PlayerGlitchPackets.StateS2C state, int row, long hash, long time, double rowWidth) {
        long category = Math.floorMod(hash, 20L);
        double configured = Math.clamp(state.maxOffset(), 0.0F, 0.12F);
        double maximum = Math.min(rowWidth * 0.18D, rowWidth * configured);
        if (rowWidth < 0.08D) {
            maximum = Math.min(maximum, rowWidth * 0.08D);
        }
        if (maximum < 0.001D || category < 7L) {
            return 0.0D;
        }

        long updatePeriod = 4L + Math.floorMod(hash >>> 8, 9L);
        long phaseOffset = Math.floorMod(hash >>> 20, updatePeriod);
        long localTime = time + phaseOffset;
        long keyframe = Math.floorDiv(localTime, updatePeriod);
        double progress = Math.floorMod(localTime, updatePeriod) / (double) updatePeriod;
        double smooth = progress * progress * (3.0D - 2.0D * progress);

        long segment = Math.floorDiv(row, 3L);
        long segmentHash = mix(SEGMENT_SALT * (segment + 1L) ^ state.seed());
        double segmentShift = randomShift(segmentHash, keyframe, maximum) * 0.35D;
        double fromShift = randomShift(hash, keyframe, maximum);
        double toShift = randomShift(hash, keyframe + 1L, maximum);
        return segmentShift + (fromShift + (toShift - fromShift) * smooth) * 0.65D;
    }

    private static double randomShift(long rowHash, long keyframe, double maximum) {
        long value = mix(rowHash ^ SHIFT_SALT * (keyframe + 1L));
        long category = Math.floorMod(value >>> 4, 10L);
        if (category < 2L) {
            return 0.0D;
        }
        double strength = category < 6L
                ? 0.25D + unit(value >>> 16) * 0.30D
                : 0.60D + unit(value >>> 32) * 0.40D;
        double direction = (value & 1L) == 0L ? -1.0D : 1.0D;
        return direction * maximum * strength;
    }

    private static void emit(VertexConsumer out, Matrix4f matrix, Vec3d camera, Vec3d center,
                             Vec3d right, Vec3d up, double x0, double x1,
                             double y0, double y1, int rgb, int alpha) {
        point(out, matrix, camera, center.add(right.multiply(x0)).add(up.multiply(y0)), rgb, alpha);
        point(out, matrix, camera, center.add(right.multiply(x1)).add(up.multiply(y0)), rgb, alpha);
        point(out, matrix, camera, center.add(right.multiply(x1)).add(up.multiply(y1)), rgb, alpha);
        point(out, matrix, camera, center.add(right.multiply(x0)).add(up.multiply(y1)), rgb, alpha);
    }

    private static void point(VertexConsumer out, Matrix4f matrix, Vec3d camera, Vec3d pos, int rgb, int alpha) {
        out.vertex(matrix, (float) (pos.x - camera.x), (float) (pos.y - camera.y), (float) (pos.z - camera.z))
                .color((rgb >>> 16) & 255, (rgb >>> 8) & 255, rgb & 255, Math.clamp(alpha, 0, 255));
    }

    private static float unit(long value) {
        return (float) ((value & 0xFFFFFFL) / 16777215.0D);
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
