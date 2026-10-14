package com.kuilunfuzhe.monvhua.features.daynight;

/** Standalone regression suite: runs the actual integrator without Minecraft or a world. */
public final class DayNightControllerTest {
    private static void eq(long expected, long actual) {
        if (expected != actual) throw new AssertionError("expected " + expected + ", got " + actual);
    }
    private static long advance(DayNightController clock, DayNightConfig config, long time, int ticks) {
        for (int i = 0; i < ticks; i++) {
            long next = clock.advance(time, config);
            if (next < time) throw new AssertionError("Time moved backwards");
            time = next;
        }
        return time;
    }
    public static void main(String[] args) {
        DayNightConfig defaults = DayNightConfig.defaults();
        DayNightController clock = new DayNightController();
        long time = 0;
        int[] boundaries = {1000,12000,13000,24000};
        for (int phase = 0; phase < 4; phase++) {
            time = advance(clock, defaults, time, defaults.seconds(phase) * 20);
            eq(boundaries[phase], time);
        }
        time = advance(clock, defaults, time, 43200 * 100);
        eq(24000L * 101, time);
        // Full cycles from arbitrary solar coordinates, including negative / multi-day time.
        for (long start : new long[]{-1, 1, 999, 1000, 11999, 12000, 12999, 13000, 23999, 24001, 9000001}) {
            for (DayNightConfig config : new DayNightConfig[]{defaults,
                    new DayNightConfig(true, 1, 1, 1, 1), new DayNightConfig(true, 7, 13, 3, 17)}) {
                clock = new DayNightController();
                int cycle = (config.dawn()+config.day()+config.dusk()+config.night())*20;
                eq(start + 24000, advance(clock, config, start, cycle));
            }
        }
        // External time changes must re-anchor rather than pull the world back.
        clock = new DayNightController(); advance(clock, defaults, 0, 999);
        DayNightController fresh = new DayNightController();
        eq(fresh.advance(18000, defaults), clock.advance(18000, defaults));
        eq(fresh.advance(500, defaults), clock.advance(500, defaults));
        // Maximum durations stay within long arithmetic, and retain sub-tick progress.
        DayNightConfig max = new DayNightConfig(true, 86400,86400,86400,86400);
        clock = new DayNightController(); eq(1000, advance(clock,max,0,86400*20));
        clock.anchor(13000, defaults); eq(13000, clock.expected);
        eq(24000, advance(clock, defaults, 13000,1140*20));
        // Runtime state round-trip: exact continuation, not a fresh partial-tick anchor.
        clock = new DayNightController(); time = advance(clock, defaults, 0, 17);
        DayNightController restored = new DayNightController();
        restored.phase=clock.phase; restored.progress=clock.progress; restored.expected=clock.expected;
        eq(advance(clock,defaults,time,43200), advance(restored,defaults,time,43200));
        if (new DayNightConfig(true,0,1,1,1).valid() || new DayNightConfig(true,1,86401,1,1).valid())
            throw new AssertionError("Invalid durations accepted");
        System.out.println("PASS: boundaries, 101 cycles, arbitrary anchors, fast phases, max durations, external changes, restored progress, validation");
    }
}
