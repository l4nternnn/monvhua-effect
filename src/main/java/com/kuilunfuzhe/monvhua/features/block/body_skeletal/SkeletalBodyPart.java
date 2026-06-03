package com.kuilunfuzhe.monvhua.features.block.body_skeletal;

import org.jetbrains.annotations.Nullable;

public enum SkeletalBodyPart {
    TORSO("torso", null),
    HEAD("head", "torso"),
    LEFT_ARM("left_arm", "torso"),
    RIGHT_ARM("right_arm", "torso"),
    LEFT_LEG("left_leg", "torso"),
    RIGHT_LEG("right_leg", "torso");

    private final String id;
    @Nullable
    private final String parentId;

    SkeletalBodyPart(String id, @Nullable String parentId) {
        this.id = id;
        this.parentId = parentId;
    }

    public String id() {
        return id;
    }

    @Nullable
    public String parentId() {
        return parentId;
    }

    public String registryPath() {
        return "skeletal_" + id;
    }
}
