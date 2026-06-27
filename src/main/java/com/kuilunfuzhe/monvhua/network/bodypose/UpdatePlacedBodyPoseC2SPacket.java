package com.kuilunfuzhe.monvhua.network.bodypose;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public record UpdatePlacedBodyPoseC2SPacket(int entityId, String poseMode, float[] poseValues, float[] bendValues,
                                            List<PlaceTrueSkeletalBodyC2SPacket.BonePose> bones,
                                            float offsetX, float offsetY, float offsetZ,
                                            float rotationPitch, float rotationYaw, float rotationRoll,
                                            float modelScale) implements CustomPayload {
    public static final Id<UpdatePlacedBodyPoseC2SPacket> ID =
            new Id<>(Identifier.of("monvhua", "update_placed_body_pose"));
    public static final PacketCodec<RegistryByteBuf, UpdatePlacedBodyPoseC2SPacket> CODEC =
            PacketCodec.of(UpdatePlacedBodyPoseC2SPacket::write, UpdatePlacedBodyPoseC2SPacket::new);
    private static final int MAX_BONES = 64;
    private static boolean registered = false;

    public UpdatePlacedBodyPoseC2SPacket {
        poseMode = poseMode == null ? "" : poseMode;
        poseValues = normalizePoseValues(poseValues);
        bendValues = normalizeBendValues(bendValues);
        bones = List.copyOf(bones == null ? List.of() : bones.subList(0, Math.min(bones.size(), MAX_BONES)));
        modelScale = modelScale <= 0.0F ? 1.0F : modelScale;
    }

    private UpdatePlacedBodyPoseC2SPacket(RegistryByteBuf buf) {
        this(buf.readVarInt(), buf.readString(), readPoseValues(buf), readBendValues(buf), readBones(buf),
                buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    private void write(RegistryByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeString(poseMode);
        for (float value : poseValues) {
            buf.writeFloat(value);
        }
        for (float value : bendValues) {
            buf.writeFloat(value);
        }
        buf.writeVarInt(Math.min(bones.size(), MAX_BONES));
        for (int i = 0; i < Math.min(bones.size(), MAX_BONES); i++) {
            PlaceTrueSkeletalBodyC2SPacket.BonePose pose = bones.get(i);
            buf.writeString(pose.name());
            buf.writeFloat(pose.pitch());
            buf.writeFloat(pose.yaw());
            buf.writeFloat(pose.roll());
            buf.writeFloat(pose.offsetX());
            buf.writeFloat(pose.offsetY());
            buf.writeFloat(pose.offsetZ());
            buf.writeFloat(pose.scale());
            buf.writeBoolean(pose.visible());
        }
        buf.writeFloat(offsetX);
        buf.writeFloat(offsetY);
        buf.writeFloat(offsetZ);
        buf.writeFloat(rotationPitch);
        buf.writeFloat(rotationYaw);
        buf.writeFloat(rotationRoll);
        buf.writeFloat(modelScale);
    }

    private static float[] readPoseValues(RegistryByteBuf buf) {
        float[] values = new float[PlacePosedBodyC2SPacket.POSE_VALUE_COUNT];
        for (int i = 0; i < values.length; i++) {
            values[i] = buf.readFloat();
        }
        return values;
    }

    private static float[] readBendValues(RegistryByteBuf buf) {
        float[] values = new float[PlacePosedBodyC2SPacket.BEND_VALUE_COUNT];
        for (int i = 0; i < values.length; i++) {
            values[i] = buf.readFloat();
        }
        return values;
    }

    private static List<PlaceTrueSkeletalBodyC2SPacket.BonePose> readBones(RegistryByteBuf buf) {
        int count = Math.min(buf.readVarInt(), MAX_BONES);
        List<PlaceTrueSkeletalBodyC2SPacket.BonePose> bones = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            bones.add(new PlaceTrueSkeletalBodyC2SPacket.BonePose(buf.readString(),
                    buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readFloat(), buf.readBoolean()));
        }
        return bones;
    }

    private static float[] normalizePoseValues(float[] values) {
        if (values == null) {
            float[] defaults = new float[PlacePosedBodyC2SPacket.POSE_VALUE_COUNT];
            for (int part = 0; part < PlacePosedBodyC2SPacket.PART_COUNT; part++) {
                defaults[part * PlacePosedBodyC2SPacket.POSE_VALUE_STRIDE + 3] = 1.0F;
            }
            return defaults;
        }
        if (values.length == PlacePosedBodyC2SPacket.POSE_VALUE_COUNT) {
            return values.clone();
        }
        throw new IllegalArgumentException("Expected " + PlacePosedBodyC2SPacket.POSE_VALUE_COUNT
                + " pose values, got " + values.length);
    }

    private static float[] normalizeBendValues(float[] values) {
        if (values == null) {
            return new float[PlacePosedBodyC2SPacket.BEND_VALUE_COUNT];
        }
        if (values.length == PlacePosedBodyC2SPacket.BEND_VALUE_COUNT) {
            return values.clone();
        }
        throw new IllegalArgumentException("Expected " + PlacePosedBodyC2SPacket.BEND_VALUE_COUNT
                + " bend values, got " + values.length);
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
