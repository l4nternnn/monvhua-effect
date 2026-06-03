package com.kuilunfuzhe.monvhua.network.bodypose;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ApplySkeletalPoseC2SPacket(float[] poseValues, float[] bendValues, int radius) implements CustomPayload {
    public static final Id<ApplySkeletalPoseC2SPacket> ID =
            new Id<>(Identifier.of("monvhua", "apply_skeletal_pose"));
    public static final PacketCodec<RegistryByteBuf, ApplySkeletalPoseC2SPacket> CODEC =
            PacketCodec.of(ApplySkeletalPoseC2SPacket::write, ApplySkeletalPoseC2SPacket::new);

    private static boolean registered = false;
    private static final int DEFAULT_RADIUS = 8;
    private static final int MAX_RADIUS = 24;
    public static final int BEND_VALUE_STRIDE = 3;
    public static final int BEND_VALUE_COUNT = PlacePosedBodyC2SPacket.PART_COUNT * BEND_VALUE_STRIDE;

    public ApplySkeletalPoseC2SPacket(float[] poseValues) {
        this(poseValues, null, DEFAULT_RADIUS);
    }

    public ApplySkeletalPoseC2SPacket(float[] poseValues, float[] bendValues) {
        this(poseValues, bendValues, DEFAULT_RADIUS);
    }

    public ApplySkeletalPoseC2SPacket {
        poseValues = normalizePoseValues(poseValues);
        bendValues = normalizeBendValues(bendValues);
        radius = Math.max(1, Math.min(MAX_RADIUS, radius));
    }

    private ApplySkeletalPoseC2SPacket(RegistryByteBuf buf) {
        this(readPoseValues(buf), readBendValues(buf), buf.readVarInt());
    }

    private void write(RegistryByteBuf buf) {
        for (float value : this.poseValues) {
            buf.writeFloat(value);
        }
        for (float value : this.bendValues) {
            buf.writeFloat(value);
        }
        buf.writeVarInt(this.radius);
    }

    private static float[] readPoseValues(RegistryByteBuf buf) {
        float[] values = new float[PlacePosedBodyC2SPacket.POSE_VALUE_COUNT];
        for (int i = 0; i < values.length; i++) {
            values[i] = buf.readFloat();
        }
        return values;
    }

    private static float[] readBendValues(RegistryByteBuf buf) {
        float[] values = new float[BEND_VALUE_COUNT];
        for (int i = 0; i < values.length; i++) {
            values[i] = buf.readFloat();
        }
        return values;
    }

    private static float[] normalizePoseValues(float[] values) {
        if (values == null) {
            return createDefaultPoseValues();
        }
        if (values.length == PlacePosedBodyC2SPacket.POSE_VALUE_COUNT) {
            return values.clone();
        }
        if (values.length == PlacePosedBodyC2SPacket.ROTATION_VALUE_COUNT) {
            float[] normalized = createDefaultPoseValues();
            for (int part = 0; part < PlacePosedBodyC2SPacket.PART_COUNT; part++) {
                normalized[part * PlacePosedBodyC2SPacket.POSE_VALUE_STRIDE] = values[part * 3];
                normalized[part * PlacePosedBodyC2SPacket.POSE_VALUE_STRIDE + 1] = values[part * 3 + 1];
                normalized[part * PlacePosedBodyC2SPacket.POSE_VALUE_STRIDE + 2] = values[part * 3 + 2];
            }
            return normalized;
        }
        throw new IllegalArgumentException("Expected " + PlacePosedBodyC2SPacket.ROTATION_VALUE_COUNT
                + " or " + PlacePosedBodyC2SPacket.POSE_VALUE_COUNT + " pose values, got " + values.length);
    }

    private static float[] normalizeBendValues(float[] values) {
        if (values == null) {
            return new float[BEND_VALUE_COUNT];
        }
        if (values.length == BEND_VALUE_COUNT) {
            return values.clone();
        }
        throw new IllegalArgumentException("Expected " + BEND_VALUE_COUNT + " bend values, got " + values.length);
    }

    private static float[] createDefaultPoseValues() {
        float[] values = new float[PlacePosedBodyC2SPacket.POSE_VALUE_COUNT];
        for (int part = 0; part < PlacePosedBodyC2SPacket.PART_COUNT; part++) {
            values[part * PlacePosedBodyC2SPacket.POSE_VALUE_STRIDE + 3] = 1.0F;
        }
        return values;
    }

    public static void register() {
        if (!registered) {
            PayloadTypeRegistry.playC2S().register(ID, CODEC);
            registered = true;
        }
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
