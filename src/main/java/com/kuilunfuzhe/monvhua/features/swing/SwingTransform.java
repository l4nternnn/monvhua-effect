package com.kuilunfuzhe.monvhua.features.swing;

import net.minecraft.util.math.Vec3d;

public final class SwingTransform {
    private SwingTransform() {}

    /** The suspension spacing is the seat width; motion is perpendicular to it. */
    public static Vec3d rotate(Vec3d v, float angle, boolean widthAlongZ) {
        double c = Math.cos(angle), s = Math.sin(angle);
        return widthAlongZ ? new Vec3d(v.x * c - v.y * s, v.x * s + v.y * c, v.z)
                : new Vec3d(v.x, v.y * c - v.z * s, v.y * s + v.z * c);
    }

    public static Vec3d localToWorld(Vec3d local, Vec3d pivot, float angle, boolean zAxis) {
        return pivot.add(rotate(local, angle, zAxis));
    }
}
