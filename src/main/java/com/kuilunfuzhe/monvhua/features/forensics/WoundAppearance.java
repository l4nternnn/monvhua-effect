package com.kuilunfuzhe.monvhua.features.forensics;

/** Pixel-art wound stamps in model pixels. Appearance is illustrative, not a depth measurement. */
public final class WoundAppearance {
    private WoundAppearance() {}

    public record Stamp(String kind, double cosine, double sine, double length, double width,
                        double bend, double phase, int severity) {
        public double radius() { return length + width + Math.abs(bend); }

        public int color(double dx, double dy) {
            double x = dx * cosine + dy * sine;
            double y = -dx * sine + dy * cosine;
            double t = y / length;
            if (Math.abs(t) >= 1) return 0;
            double center = bend * Math.sin(t * Math.PI) * (1 - t * t);
            double irregular = 1 + .09 * Math.sin(t * 13 + phase) + .045 * Math.sin(t * 31 - phase);
            double edge = width * Math.sqrt(1 - t * t) * irregular;
            double across = Math.abs(x - center) / edge;
            if (across >= 1) return 0;
            double radius = Math.sqrt(across * across + t * t * (1 - across * across));
            // Discrete bands preserve the opening silhouette rather than blurring it into a spot.
            int band = radius < .42 ? 0 : radius < .70 ? 1 : radius < .88 ? 2 : radius < .96 ? 3 : 4;
            return (kind.equals("blunt") ? BRUISE : OPEN_WOUND)[severity][band];
        }
    }

    // Center -> interior -> rim -> outer inflammation -> soft perimeter, in ARGB.
    private static final int[][] OPEN_WOUND = {
        {0xE8CE5A61, 0xE8EE8288, 0xDDF5A6AA, 0x99F6BDC0, 0x40FADADC},
        {0xFFBB2928, 0xFFFF382B, 0xF5F16C72, 0xB8F6A1A5, 0x50FAD4D6},
        {0xFF300710, 0xFF780C1D, 0xFFE51C2C, 0xDBFA555E, 0x80ED959D}
    };
    private static final int[][] BRUISE = {
        {0x888E719A, 0x779F80AD, 0x66CC95B0, 0x40E6B3C6, 0x18F1D0DC},
        {0xB5754B88, 0xA598629E, 0x88C67EAA, 0x55E3A1B8, 0x20EEC2D0},
        {0xD93E294F, 0xC56B3B79, 0xAD9A507E, 0x77C77E9C, 0x30DEA5BB}
    };

    public static Stamp create(String kind, int seed) {
        return create(kind, seed, "moderate");
    }

    public static Stamp create(String kind, int seed, String severity) {
        if (!kind.equals("cut") && !kind.equals("puncture") && !kind.equals("blunt")) return null;
        double variant = (Integer.toUnsignedLong(seed) % 997) / 996.0;
        double angle = (variant - .5) * 1.25;
        double phase = variant * Math.PI * 2;
        // Full dimensions: cuts ~5 x 1.6 px; punctures ~2.8 x 1.9 px; bruises ~4 x 3.4 px.
        double length = kind.equals("cut") ? 2.5 : kind.equals("puncture") ? 1.4 : 2.0;
        double width = kind.equals("cut") ? .8 : kind.equals("puncture") ? .95 : 1.7;
        double bend = kind.equals("cut") ? (Math.floorMod(seed, 7) == 0 ? 0 : .4 + variant * .25) : .12;
        return new Stamp(kind, Math.cos(angle), Math.sin(angle), length, width, bend, phase,
                switch (severity) { case "mild" -> 0; case "severe" -> 2; default -> 1; });
    }

}
