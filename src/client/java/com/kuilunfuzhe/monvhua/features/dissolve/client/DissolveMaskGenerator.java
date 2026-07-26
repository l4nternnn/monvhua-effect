package com.kuilunfuzhe.monvhua.features.dissolve.client;

import com.kuilunfuzhe.monvhua.features.dissolve.DissolveProfile;

final class DissolveMaskGenerator {
    private DissolveMaskGenerator() {
    }

    static Mask mask(int pixelX, int pixelY, int imageWidth, int imageHeight,
                     int elapsedTicks, long seed, DissolveProfile profile) {
        DissolveProfile safeProfile = profile == null ? DissolveProfile.DEFAULT : profile;
        DissolveSkinMapper.BodyPoint point = DissolveSkinMapper.bodyPoint(pixelX, pixelY, imageWidth, imageHeight);
        Sweep sweep = sweep(elapsedTicks, safeProfile);
        float score = score(point, sweep)
                + stableNoise(seed, pixelX, pixelY) * safeProfile.noiseStrength()
                + point.depthDelay() * safeProfile.surfaceDepthDelay();
        boolean complete = elapsedTicks >= safeProfile.durationTicks();
        boolean transparent = complete || score <= sweep.threshold();
        boolean edge = !complete && Math.abs(score - sweep.threshold()) <= safeProfile.edgeBand();
        return new Mask(transparent, edge, point);
    }

    static Sweep sweep(int elapsedTicks, DissolveProfile profile) {
        DissolveProfile safeProfile = profile == null ? DissolveProfile.DEFAULT : profile;
        float progress = clamp(elapsedTicks / (float) safeProfile.durationTicks());
        double angle = Math.toRadians(safeProfile.angleDegrees());
        float dirX = (float) Math.sin(angle);
        float dirY = (float) Math.cos(angle);
        float length = (float) Math.sqrt(dirX * dirX + dirY * dirY);
        if (length <= 0.0001F) {
            dirX = 0.0F;
            dirY = 1.0F;
        } else {
            dirX /= length;
            dirY /= length;
        }
        float minScore = minCornerScore(safeProfile, dirX, dirY);
        float maxScore = maxCornerScore(safeProfile, dirX, dirY);
        float start = minScore - safeProfile.startPadding();
        float end = maxScore + safeProfile.endPadding();
        return new Sweep(
                progress,
                safeProfile.originX(),
                safeProfile.originY(),
                dirX,
                dirY,
                -dirY,
                dirX,
                lerp(start, end, progress),
                minScore,
                maxScore
        );
    }

    private static float score(DissolveSkinMapper.BodyPoint point, Sweep sweep) {
        return (point.x() - sweep.originX()) * sweep.dirX()
                + (point.y() - sweep.originY()) * sweep.dirY();
    }

    private static float minCornerScore(DissolveProfile profile, float dirX, float dirY) {
        return Math.min(
                Math.min(cornerScore(0.0F, 0.0F, profile, dirX, dirY),
                        cornerScore(1.0F, 0.0F, profile, dirX, dirY)),
                Math.min(cornerScore(0.0F, 1.0F, profile, dirX, dirY),
                        cornerScore(1.0F, 1.0F, profile, dirX, dirY))
        );
    }

    private static float maxCornerScore(DissolveProfile profile, float dirX, float dirY) {
        return Math.max(
                Math.max(cornerScore(0.0F, 0.0F, profile, dirX, dirY),
                        cornerScore(1.0F, 0.0F, profile, dirX, dirY)),
                Math.max(cornerScore(0.0F, 1.0F, profile, dirX, dirY),
                        cornerScore(1.0F, 1.0F, profile, dirX, dirY))
        );
    }

    private static float cornerScore(float x, float y, DissolveProfile profile, float dirX, float dirY) {
        return (x - profile.originX()) * dirX + (y - profile.originY()) * dirY;
    }

    private static float lerp(float from, float to, float progress) {
        return from + (to - from) * progress;
    }

    private static float stableNoise(long seed, int x, int y) {
        long value = seed;
        value ^= (long) x * 0x9E3779B97F4A7C15L;
        value ^= (long) y * 0xC2B2AE3D27D4EB4FL;
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return (((value >>> 24) & 0xFFFF) / 65535.0F) - 0.5F;
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    record Mask(boolean transparent, boolean edge, DissolveSkinMapper.BodyPoint point) {
    }

    record Sweep(float progress,
                 float originX, float originY,
                 float dirX, float dirY,
                 float tangentX, float tangentY,
                 float threshold,
                 float minScore, float maxScore) {
    }
}
