package com.kuilunfuzhe.monvhua.gui.body.bodypose.viewport;

/** One pointer transaction, in logical pixels. The dead zone is consumed once. */
public final class DragPointer {
    public enum State { ARMED, DRAGGING, ENDED }
    public record Point(double x, double y) {}
    private final double startX, startY, threshold;
    private double consumedX, consumedY;
    private State state = State.ARMED;

    public DragPointer(double x, double y, double threshold) {
        startX = x;
        startY = y;
        this.threshold = threshold;
    }

    public Point update(double x, double y) {
        if (state == State.ENDED || !Double.isFinite(x) || !Double.isFinite(y)) return null;
        if (state == State.ARMED) {
            double dx = x - startX, dy = y - startY;
            double distance = Math.hypot(dx, dy);
            if (distance <= threshold) return null;
            consumedX = dx / distance * threshold;
            consumedY = dy / distance * threshold;
            state = State.DRAGGING;
        }
        return new Point(x - consumedX, y - consumedY);
    }

    public State state() { return state; }
    public void end() { state = State.ENDED; }
}
