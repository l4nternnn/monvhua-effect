package com.kuilunfuzhe.monvhua.renderer.worlddisplay;

/** Fixed virtual camera parameters for a block rendered into a bubble texture. */
public record WorldDisplayViewProfile(
        float pitch,
        float yaw,
        float roll,
        float scale,
        float offsetX,
        float offsetY
) {
    public static final WorldDisplayViewProfile CHEST =
            new WorldDisplayViewProfile(0.0F, -45.0F, 0.0F, 1.0F, 0.0F, 0.05F);
}
