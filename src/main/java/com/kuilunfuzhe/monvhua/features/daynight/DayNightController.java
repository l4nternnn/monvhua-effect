package com.kuilunfuzhe.monvhua.features.daynight;

/** Pure integer integrator. One server tick is 11000 units of elapsed time.
 * Both solar spans divide 11000, so re-anchoring and boundary carry are exact. */
public final class DayNightController {
    private static final int[] START = {0, 1000, 12000, 13000};
    private static final int[] SPAN = {1000, 11000, 1000, 11000};
    private static final long UNIT = 11000;
    public int phase;
    public long progress;
    public long expected = Long.MIN_VALUE;

    public void anchor(long time, DayNightConfig config) {
        int solar = (int) Math.floorMod(time, 24000L);
        phase = solar < 1000 ? 0 : solar < 12000 ? 1 : solar < 13000 ? 2 : 3;
        progress = (solar - START[phase]) * (UNIT / SPAN[phase]) * config.seconds(phase) * 20L;
        expected = time;
    }

    public long advance(long time, DayNightConfig config) {
        if (time != expected || phase < 0 || phase > 3 || progress < 0
                || progress >= duration(config)) anchor(time, config);
        long dayBase = time - Math.floorMod(time, 24000L);
        long remaining = UNIT;
        while (remaining > 0) {
            long used = Math.min(remaining, duration(config) - progress);
            progress += used;
            remaining -= used;
            if (progress == duration(config)) {
                progress = 0;
                phase = (phase + 1) % 4;
                if (phase == 0) dayBase += 24000;
            }
        }
        expected = dayBase + START[phase] + progress * SPAN[phase] / duration(config);
        return expected;
    }

    private long duration(DayNightConfig config) { return config.seconds(phase) * 20L * UNIT; }
}
