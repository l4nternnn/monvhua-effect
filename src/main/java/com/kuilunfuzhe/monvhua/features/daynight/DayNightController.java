package com.kuilunfuzhe.monvhua.features.daynight;

/** Exact rational cycle-time. The anchor's span (<=24000) is the fixed denominator.
 * Adding whole server ticks never changes it, even across arbitrary segment boundaries.
 * Worst-case intermediate arithmetic remains below 10^15 for the validated configuration. */
public final class DayNightController {
    public long elapsed;
    public int denominator=1;
    public long expected=Long.MIN_VALUE;

    public void anchor(long time,DayNightConfig config) {
        int offset=(int)Math.floorMod(time-config.segments().getFirst().start(),24000L);
        long ticksBefore=0;int solarBefore=0;
        for(DayNightSegment s:config.segments()) {
            if(offset<solarBefore+s.span()) {
                denominator=s.span();
                elapsed=ticksBefore*denominator+(offset-solarBefore)*s.seconds()*20L;
                expected=time;return;
            }
            ticksBefore+=s.seconds()*20L;solarBefore+=s.span();
        }
        throw new IllegalArgumentException("Invalid solar coverage");
    }
    public long advance(long time,DayNightConfig config) {
        if(time!=expected||denominator<1||denominator>24000||elapsed<0||elapsed>=config.totalTicks()*denominator)
            anchor(time,config);
        long cycle=config.totalTicks()*denominator;
        elapsed+=denominator;
        long wrapped=elapsed/cycle; elapsed%=cycle;
        int origin=config.segments().getFirst().start();
        long base=time-Math.floorMod(time-origin,24000L)+wrapped*24000L;
        long ticksBefore=0;int solarBefore=0;
        for(DayNightSegment s:config.segments()) {
            long duration=s.seconds()*20L;
            if(elapsed<(ticksBefore+duration)*denominator) {
                expected=base+solarBefore+(elapsed-ticksBefore*denominator)*s.span()/(duration*denominator);
                return expected;
            }
            ticksBefore+=duration;solarBefore+=s.span();
        }
        throw new IllegalStateException("Invalid cycle position");
    }
}
