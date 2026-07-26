package com.kuilunfuzhe.monvhua.features.dissolve;

import net.minecraft.network.RegistryByteBuf;

public record DissolveProfile(
        int durationTicks,
        float edgeBand,
        float noiseStrength,
        float edgeAlpha,
        float originX,
        float originY,
        float angleDegrees,
        float startPadding,
        float endPadding,
        float surfaceDepthDelay,
        int textureUploadIntervalTicks,
        int particlesPerTick,
        int particleDelayTicks,
        float particleTrailRatio,
        double particleLeftSpeed,
        double particleUpSpeed,
        double particleRandomSpeed,
        boolean freezeTarget,
        boolean hideNameTag
) {
    public static final DissolveProfile DEFAULT = new DissolveProfile(
            100,
            0.045F,
            0.08F,
            0.65F,
            0.0F,
            0.0F,
            35.0F,
            0.02F,
            0.10F,
            0.08F,
            2,
            12,
            4,
            0.03F,
            0.045D,
            0.075D,
            0.025D,
            true,
            true
    );

    public DissolveProfile {
        durationTicks = clamp(durationTicks, 1, 20 * 60);
        edgeBand = clamp(edgeBand, 0.005F, 0.25F);
        noiseStrength = clamp(noiseStrength, 0.0F, 0.35F);
        edgeAlpha = clamp(edgeAlpha, 0.0F, 1.0F);
        originX = clamp(originX, 0.0F, 1.0F);
        originY = clamp(originY, 0.0F, 1.0F);
        angleDegrees = clamp(angleDegrees, -180.0F, 180.0F);
        startPadding = clamp(startPadding, 0.0F, 0.5F);
        endPadding = clamp(endPadding, 0.0F, 0.5F);
        surfaceDepthDelay = clamp(surfaceDepthDelay, 0.0F, 0.35F);
        textureUploadIntervalTicks = clamp(textureUploadIntervalTicks, 1, 20);
        particlesPerTick = clamp(particlesPerTick, 0, 128);
        particleDelayTicks = clamp(particleDelayTicks, 0, 20 * 5);
        particleTrailRatio = clamp(particleTrailRatio, 0.0F, 0.25F);
        particleLeftSpeed = clamp(particleLeftSpeed, 0.0D, 1.0D);
        particleUpSpeed = clamp(particleUpSpeed, 0.0D, 1.0D);
        particleRandomSpeed = clamp(particleRandomSpeed, 0.0D, 1.0D);
    }

    public static DissolveProfile read(RegistryByteBuf buf) {
        return new DissolveProfile(
                buf.readVarInt(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readFloat(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readBoolean(),
                buf.readBoolean()
        );
    }

    public void write(RegistryByteBuf buf) {
        buf.writeVarInt(durationTicks);
        buf.writeFloat(edgeBand);
        buf.writeFloat(noiseStrength);
        buf.writeFloat(edgeAlpha);
        buf.writeFloat(originX);
        buf.writeFloat(originY);
        buf.writeFloat(angleDegrees);
        buf.writeFloat(startPadding);
        buf.writeFloat(endPadding);
        buf.writeFloat(surfaceDepthDelay);
        buf.writeVarInt(textureUploadIntervalTicks);
        buf.writeVarInt(particlesPerTick);
        buf.writeVarInt(particleDelayTicks);
        buf.writeFloat(particleTrailRatio);
        buf.writeDouble(particleLeftSpeed);
        buf.writeDouble(particleUpSpeed);
        buf.writeDouble(particleRandomSpeed);
        buf.writeBoolean(freezeTarget);
        buf.writeBoolean(hideNameTag);
    }

    public DissolveProfile withDurationTicks(int durationTicks) {
        return new DissolveProfile(
                durationTicks,
                edgeBand,
                noiseStrength,
                edgeAlpha,
                originX,
                originY,
                angleDegrees,
                startPadding,
                endPadding,
                surfaceDepthDelay,
                textureUploadIntervalTicks,
                particlesPerTick,
                particleDelayTicks,
                particleTrailRatio,
                particleLeftSpeed,
                particleUpSpeed,
                particleRandomSpeed,
                freezeTarget,
                hideNameTag
        );
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
