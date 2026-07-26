package com.kuilunfuzhe.monvhua.features.dissolve.client;

import java.util.ArrayList;
import java.util.List;

final class DissolveSkinMapper {
    private static final List<Region> REGIONS = createRegions();

    private DissolveSkinMapper() {
    }

    static BodyPoint bodyPoint(int pixelX, int pixelY, int imageWidth, int imageHeight) {
        double x64 = (pixelX + 0.5D) * 64.0D / Math.max(1, imageWidth);
        double y64 = (pixelY + 0.5D) * 64.0D / Math.max(1, imageHeight);
        for (Region region : REGIONS) {
            if (region.contains(x64, y64)) {
                double u = (x64 - region.x()) / Math.max(0.0001D, region.width());
                double v = (y64 - region.y()) / Math.max(0.0001D, region.height());
                return new BodyPoint(
                        (float) lerp(region.bodyX0(), region.bodyX1(), u),
                        (float) lerp(region.bodyY0(), region.bodyY1(), v),
                        region.depthDelay()
                );
            }
        }
        return new BodyPoint((float) (x64 / 64.0D), (float) (y64 / 64.0D), 0.5F);
    }

    private static List<Region> createRegions() {
        List<Region> regions = new ArrayList<>();
        addCuboid(regions, 0, 0, 8, 8, 8, 0.34D, 0.66D, 0.00D, 0.22D);
        addCuboid(regions, 32, 0, 8, 8, 8, 0.34D, 0.66D, 0.00D, 0.22D);
        addCuboid(regions, 16, 16, 8, 12, 4, 0.36D, 0.64D, 0.22D, 0.58D);
        addCuboid(regions, 16, 32, 8, 12, 4, 0.36D, 0.64D, 0.22D, 0.58D);
        addCuboid(regions, 40, 16, 4, 12, 4, 0.14D, 0.34D, 0.22D, 0.78D);
        addCuboid(regions, 40, 32, 4, 12, 4, 0.14D, 0.34D, 0.22D, 0.78D);
        addCuboid(regions, 32, 48, 4, 12, 4, 0.66D, 0.86D, 0.22D, 0.78D);
        addCuboid(regions, 48, 48, 4, 12, 4, 0.66D, 0.86D, 0.22D, 0.78D);
        addCuboid(regions, 0, 16, 4, 12, 4, 0.32D, 0.50D, 0.58D, 1.00D);
        addCuboid(regions, 0, 32, 4, 12, 4, 0.32D, 0.50D, 0.58D, 1.00D);
        addCuboid(regions, 16, 48, 4, 12, 4, 0.50D, 0.68D, 0.58D, 1.00D);
        addCuboid(regions, 0, 48, 4, 12, 4, 0.50D, 0.68D, 0.58D, 1.00D);
        return List.copyOf(regions);
    }

    private static void addCuboid(List<Region> regions, int textureX, int textureY,
                                  int width, int height, int depth,
                                  double bodyX0, double bodyX1, double bodyY0, double bodyY1) {
        int sideY = textureY + depth;
        double sideSpan = Math.max(0.01D, (bodyX1 - bodyX0) * 0.2D);
        regions.add(new Region(textureX + depth, textureY, width, depth, bodyX0, bodyX1, bodyY0, bodyY0, 0.25F));
        regions.add(new Region(textureX + depth + width, textureY, width, depth, bodyX0, bodyX1, bodyY1, bodyY1, 0.25F));
        regions.add(new Region(textureX, sideY, depth, height, bodyX0, bodyX0 + sideSpan, bodyY0, bodyY1, 0.5F));
        regions.add(new Region(textureX + depth, sideY, width, height, bodyX0, bodyX1, bodyY0, bodyY1, 0.0F));
        regions.add(new Region(textureX + depth + width, sideY, depth, height, bodyX1 - sideSpan, bodyX1, bodyY0, bodyY1, 0.5F));
        regions.add(new Region(textureX + depth + width + depth, sideY, width, height, bodyX0, bodyX1, bodyY0, bodyY1, 1.0F));
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    record BodyPoint(float x, float y, float depthDelay) {
    }

    private record Region(double x, double y, double width, double height,
                          double bodyX0, double bodyX1, double bodyY0, double bodyY1,
                          float depthDelay) {
        boolean contains(double px, double py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }
}
