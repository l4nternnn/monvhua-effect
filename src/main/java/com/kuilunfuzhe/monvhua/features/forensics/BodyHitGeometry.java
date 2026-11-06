package com.kuilunfuzhe.monvhua.features.forensics;

import net.minecraft.util.math.Direction;

public final class BodyHitGeometry {
    private BodyHitGeometry() {}
    /** null means no surface-gravity override, i.e. ordinary vanilla gravity. */
    public static boolean normalGravity(Direction override) { return override == null || override == Direction.DOWN; }
    public static String part(double heightFraction, double rightFraction) {
        if (!Double.isFinite(heightFraction) || !Double.isFinite(rightFraction)) return "unknown";
        if (heightFraction > .78) return "head";
        if (heightFraction < .43) return rightFraction >= 0 ? "right_leg" : "left_leg";
        if (Math.abs(rightFraction) > .32) return rightFraction >= 0 ? "right_arm" : "left_arm";
        return "torso";
    }
}
