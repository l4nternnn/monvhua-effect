package com.kuilunfuzhe.monvhua.features.forensics;

public final class SummaryWheelGeometry {
    public static final int SECTORS = 6;
    public static final double STEP = Math.PI * 2 / SECTORS;
    private SummaryWheelGeometry() {}
    public static int sector(double x, double y, double inner, double outer) {
        double distance = Math.hypot(x, y);
        if (distance < inner || distance > outer) return -1;
        double angle = (Math.atan2(y, x) + Math.PI / 2 + STEP / 2 + Math.PI * 2) % (Math.PI * 2);
        return Math.min(SECTORS - 1, (int) (angle / STEP));
    }
}
