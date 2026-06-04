package com.kuilunfuzhe.monvhua.features.action;

import net.minecraft.client.MinecraftClient;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ActionPoseClientState {
    private static final Map<Integer, Pose> POSES = new ConcurrentHashMap<>();

    public static void apply(int entityId, float[] degrees, int durationTicks) {
        if (degrees == null || degrees.length < 18) return;
        float[] radians = new float[18];
        for (int i = 0; i < 18; i++) {
            radians[i] = (float) Math.toRadians(degrees[i]);
        }
        long now = clientWorldTime();
        POSES.put(entityId, new Pose(radians, now + Math.max(1, durationTicks)));
    }

    public static float[] getRadians(int entityId) {
        Pose pose = POSES.get(entityId);
        if (pose == null) return null;
        if (clientWorldTime() > pose.expiresAtTick) {
            POSES.remove(entityId);
            return null;
        }
        return pose.radians;
    }

    public static void clear() {
        POSES.clear();
    }

    private static long clientWorldTime() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.world == null ? 0L : client.world.getTime();
    }

    private record Pose(float[] radians, long expiresAtTick) {
        private Pose {
            radians = Arrays.copyOf(radians, 18);
        }
    }
}
