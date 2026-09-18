package com.kuilunfuzhe.monvhua.features.swing;

import java.util.ArrayDeque;

/** Ordered server snapshots with two-tick interpolation and bounded extrapolation. */
public final class SwingMotionTimeline {
    private static final float LIMIT = (float) Math.toRadians(75);
    private final ArrayDeque<Snapshot> samples = new ArrayDeque<>();
    private double receivedAt;
    public record Snapshot(long tick, float angle, float velocity) { }

    public void clear() { samples.clear(); }
    public void accept(long tick, float angle, float velocity, double localTick) {
        if (!Float.isFinite(angle) || !Float.isFinite(velocity)) return;
        if (!samples.isEmpty() && tick <= samples.getLast().tick) return;
        if (!samples.isEmpty() && (tick - samples.getLast().tick > 40
                || Math.abs(angle - samples.getLast().angle) > 1)) samples.clear();
        samples.addLast(new Snapshot(tick, clamp(angle), velocity));
        while (samples.size() > 12) samples.removeFirst();
        receivedAt = localTick;
    }

    public float sample(double localTick, float fallback) {
        if (samples.isEmpty()) return fallback;
        Snapshot last = samples.getLast();
        double target = last.tick + Math.max(-1, localTick - receivedAt) - 2;
        Snapshot previous = samples.getFirst();
        if (target <= previous.tick) return previous.angle;
        for (Snapshot next : samples) {
            if (next.tick > target) {
                double t = (target - previous.tick) / (next.tick - previous.tick);
                return clamp((float) (previous.angle + (next.angle - previous.angle) * t));
            }
            previous = next;
        }
        return clamp(last.angle + last.velocity * (float) Math.min(5, Math.max(0, target - last.tick)));
    }
    private static float clamp(float angle) { return Math.max(-LIMIT, Math.min(LIMIT, angle)); }
}
