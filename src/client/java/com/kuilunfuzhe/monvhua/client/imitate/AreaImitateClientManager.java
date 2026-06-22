package com.kuilunfuzhe.monvhua.client.imitate;

import com.kuilunfuzhe.monvhua.network.imitate.AreaImitateSelectPacket;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

public class AreaImitateClientManager {

    private static boolean hasAreaImitate = false;
    private static Vec3d areaCenter = null;
    private static double areaRadius = 5.0;

    public static void setAreaImitate(Vec3d center, double radius) {
        hasAreaImitate = true;
        areaCenter = center;
        areaRadius = radius;
    }

    public static void clearAreaImitate() {
        hasAreaImitate = false;
        areaCenter = null;
        areaRadius = 5.0;
    }

    public static boolean hasAreaImitate() {
        return hasAreaImitate;
    }

    public static Vec3d getAreaCenter() {
        return areaCenter;
    }

    public static double getAreaRadius() {
        return areaRadius;
    }
}