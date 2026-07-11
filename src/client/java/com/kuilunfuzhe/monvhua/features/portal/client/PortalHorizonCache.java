package com.kuilunfuzhe.monvhua.features.portal.client;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.kuilunfuzhe.monvhua.network.portal.PortalPackets;
import net.minecraft.util.math.BlockPos;

import java.util.Arrays;

public final class PortalHorizonCache {
    private static HorizonData current;
    private static long lastLogTimeMs;

    private PortalHorizonCache() {
    }

    public static void update(PortalPackets.RemoteHorizonS2C packet) {
        if (packet == null) {
            return;
        }
        HorizonData previous = current;
        if (previous != null && packet.generation() < previous.generation()) {
            return;
        }
        current = new HorizonData(
                packet.generation(),
                packet.center(),
                packet.stepBlocks(),
                packet.gridRadius(),
                packet.minY(),
                packet.maxY(),
                packet.skyColor(),
                packet.fogColor(),
                packet.heights(),
                packet.colors()
        );
        logReceived(current);
    }

    private static void logReceived(HorizonData data) {
        long now = System.currentTimeMillis();
        if (now - lastLogTimeMs < 3000L) {
            return;
        }
        lastLogTimeMs = now;
        MonvhuaMod.LOGGER.info(
                "[Monvhua] Portal horizon received: gen={}, center={}, step={}, radius={}, samples={}, y={}..{}",
                data.generation(),
                data.center(),
                data.stepBlocks(),
                data.gridRadius(),
                data.sampleCount(),
                data.minY(),
                data.maxY()
        );
    }

    public static HorizonData current() {
        return current;
    }

    public static void clear() {
        current = null;
    }

    public record HorizonData(long generation, BlockPos center, int stepBlocks, int gridRadius,
                              int minY, int maxY, int skyColor, int fogColor,
                              int[] heights, int[] colors) {
        public HorizonData {
            center = center == null ? BlockPos.ORIGIN : center.toImmutable();
            stepBlocks = Math.max(1, stepBlocks);
            gridRadius = Math.max(0, gridRadius);
            heights = heights == null ? new int[0] : Arrays.copyOf(heights, heights.length);
            colors = colors == null ? new int[0] : Arrays.copyOf(colors, colors.length);
        }

        public int sampleCount() {
            return Math.min(heights.length, colors.length);
        }

        public int averageGroundColor() {
            int count = sampleCount();
            if (count <= 0) {
                return 0xFF5F8F4E;
            }
            long red = 0L;
            long green = 0L;
            long blue = 0L;
            for (int i = 0; i < count; i++) {
                int color = colors[i];
                red += (color >> 16) & 0xFF;
                green += (color >> 8) & 0xFF;
                blue += color & 0xFF;
            }
            return 0xFF000000
                    | ((int) (red / count) << 16)
                    | ((int) (green / count) << 8)
                    | (int) (blue / count);
        }

        public float normalizedCameraHeight(double cameraY) {
            int span = Math.max(1, maxY - minY);
            return (float) ((cameraY - minY) / span);
        }

        public int side() {
            return gridRadius * 2 + 1;
        }

        public int heightAt(int x, int z) {
            int side = side();
            int index = z * side + x;
            if (index < 0 || index >= heights.length) {
                return minY;
            }
            return heights[index];
        }

        public int colorAt(int x, int z) {
            int side = side();
            int index = z * side + x;
            if (index < 0 || index >= colors.length) {
                return averageGroundColor();
            }
            return colors[index];
        }
    }
}
