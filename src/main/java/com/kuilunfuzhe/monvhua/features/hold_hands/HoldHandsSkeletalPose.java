package com.kuilunfuzhe.monvhua.features.hold_hands;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HoldHandsSkeletalPose {
    public static final String BODY_POSE_MODE = "true_skeletal";
    public static final String ARM_MODEL_SLIM = "slim";

    public static final String RIGHT_ARM_UPPER_BONE = "right_arm_on";
    public static final String RIGHT_ARM_LOWER_BONE = "right_arm_low";
    public static final String LEFT_ARM_UPPER_BONE = "left_arm_on";
    public static final String LEFT_ARM_LOWER_BONE = "left_arm_low";

    public static final String RIGHT_ARM_PART = "right_arm";
    public static final String LEFT_ARM_PART = "left_arm";

    public static final String ACTIVE_ROLE_SKIN = "hiro";
    public static final String PASSIVE_ROLE_SKIN = "ema";
    public static final HandSide ACTIVE_ROLE_HAND = HandSide.LEFT;
    public static final HandSide PASSIVE_ROLE_HAND = HandSide.RIGHT;

    public static final List<BonePose> RIGHT_HAND_DEFAULT_POSE = List.of(
            new BonePose(RIGHT_ARM_LOWER_BONE, 0.18090954F, 0.17231862F, -0.6817982F),
            new BonePose(RIGHT_ARM_UPPER_BONE, 0.0F, 11.101904F, 7.696805F)
    );

    public static final List<BonePose> LEFT_HAND_DEFAULT_POSE = List.of(
            new BonePose(LEFT_ARM_LOWER_BONE, 0.0F, 0.0F, 2.461943F),
            new BonePose(LEFT_ARM_UPPER_BONE, 6.512664F, -9.978699F, -8.4529F)
    );

    public static final List<BonePose> DEFAULT_PAIR_POSE = List.of(
            RIGHT_HAND_DEFAULT_POSE.get(0),
            RIGHT_HAND_DEFAULT_POSE.get(1),
            LEFT_HAND_DEFAULT_POSE.get(0),
            LEFT_HAND_DEFAULT_POSE.get(1)
    );

    private HoldHandsSkeletalPose() {
    }

    public static List<BonePose> defaultPoseForHand(HandSide side) {
        return side == HandSide.LEFT ? LEFT_HAND_DEFAULT_POSE : RIGHT_HAND_DEFAULT_POSE;
    }

    public static HandSide handForRole(HoldRole role) {
        return role == HoldRole.ACTIVE ? ACTIVE_ROLE_HAND : PASSIVE_ROLE_HAND;
    }

    public static String skinForRole(HoldRole role) {
        return role == HoldRole.ACTIVE ? ACTIVE_ROLE_SKIN : PASSIVE_ROLE_SKIN;
    }

    public static boolean isFollowerHand(HandSide side) {
        return side == PASSIVE_ROLE_HAND;
    }

    public static Map<String, float[]> rotationsForHand(HandSide side) {
        return toRotationMap(defaultPoseForHand(side));
    }

    public static Map<String, float[]> defaultPairRotations() {
        return toRotationMap(DEFAULT_PAIR_POSE);
    }

    public static Map<String, float[]> toRotationMap(List<BonePose> poses) {
        Map<String, float[]> rotations = new LinkedHashMap<>();
        if (poses == null) {
            return rotations;
        }
        for (BonePose pose : poses) {
            if (pose == null || pose.name().isEmpty()) {
                continue;
            }
            rotations.put(pose.name(), new float[]{pose.pitch(), pose.yaw(), pose.roll()});
        }
        return rotations;
    }

    public static NbtCompound createDefaultArmCustomData(String localSkin, HandSide side, boolean slim) {
        NbtCompound customData = createBaseCustomData(localSkin, slim);
        customData.put("true_skeletal_bones", toNbtList(defaultPoseForHand(side)));
        return customData;
    }

    public static NbtCompound createDefaultPairCustomData(String localSkin, boolean slim) {
        NbtCompound customData = createBaseCustomData(localSkin, slim);
        customData.put("true_skeletal_bones", toNbtList(DEFAULT_PAIR_POSE));
        return customData;
    }

    public static NbtCompound createBaseCustomData(String localSkin, boolean slim) {
        NbtCompound customData = new NbtCompound();
        customData.putString("body_pose_mode", BODY_POSE_MODE);
        customData.putString("local_skin", localSkin == null ? "" : localSkin);
        customData.putString("arm_model", slim ? ARM_MODEL_SLIM : "default");
        customData.putFloat("pose_model_offset_x", 0.0F);
        customData.putFloat("pose_model_offset_y", 0.0F);
        customData.putFloat("pose_model_offset_z", 0.0F);
        customData.putFloat("pose_model_pitch", 0.0F);
        customData.putFloat("pose_model_yaw", 0.0F);
        customData.putFloat("pose_model_roll", 0.0F);
        customData.putFloat("pose_model_scale", 1.0F);
        return customData;
    }

    public static NbtList toNbtList(List<BonePose> poses) {
        NbtList list = new NbtList();
        if (poses == null) {
            return list;
        }
        for (BonePose pose : poses) {
            if (pose == null || pose.name().isEmpty()) {
                continue;
            }
            list.add(pose.toNbt());
        }
        return list;
    }

    public enum HandSide {
        LEFT,
        RIGHT
    }

    public enum HoldRole {
        ACTIVE,
        PASSIVE
    }

    public record BonePose(String name, float pitch, float yaw, float roll,
                           float offsetX, float offsetY, float offsetZ,
                           float scale, boolean visible) {
        public BonePose(String name, float pitch, float yaw, float roll) {
            this(name, pitch, yaw, roll, 0.0F, 0.0F, 0.0F, 1.0F, true);
        }

        public BonePose {
            name = name == null ? "" : name;
            scale = scale <= 0.0F ? 1.0F : scale;
        }

        public NbtCompound toNbt() {
            NbtCompound bone = new NbtCompound();
            bone.putString("name", name);
            bone.putFloat("pitch", pitch);
            bone.putFloat("yaw", yaw);
            bone.putFloat("roll", roll);
            bone.putFloat("offset_x", offsetX);
            bone.putFloat("offset_y", offsetY);
            bone.putFloat("offset_z", offsetZ);
            bone.putFloat("scale", scale);
            bone.putBoolean("visible", visible);
            return bone;
        }
    }
}
