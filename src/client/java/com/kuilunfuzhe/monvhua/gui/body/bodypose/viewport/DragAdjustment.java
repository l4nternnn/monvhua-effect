package com.kuilunfuzhe.monvhua.gui.body.bodypose.viewport;

/** Integrates fine movement and rebases snapping when Ctrl changes, without a value jump. */
public final class DragAdjustment {
    private boolean snapping;
    private double lastRaw, continuous, modeOrigin, modeOutput, output;
    public DragAdjustment(boolean snapping) { this.snapping = snapping; }

    public double update(double raw, boolean fine, boolean snap, double step) {
        if (!Double.isFinite(raw)) return output;
        continuous += (raw - lastRaw) * (fine ? 0.1 : 1);
        lastRaw = raw;
        if (snap != snapping) {
            modeOrigin = continuous;
            modeOutput = output;
            snapping = snap;
        }
        double delta = continuous - modeOrigin;
        output = modeOutput + (snap ? Math.rint(delta / step) * step : delta);
        return output;
    }
}
