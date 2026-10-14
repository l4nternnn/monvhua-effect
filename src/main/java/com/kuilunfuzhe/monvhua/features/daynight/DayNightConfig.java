package com.kuilunfuzhe.monvhua.features.daynight;

public record DayNightConfig(boolean enabled, int dawn, int day, int dusk, int night) {
    public static DayNightConfig defaults() { return new DayNightConfig(false, 60, 900, 60, 1140); }
    public boolean valid() {
        return validSeconds(dawn) && validSeconds(day) && validSeconds(dusk) && validSeconds(night);
    }
    private static boolean validSeconds(int value) { return value >= 1 && value <= 86400; }
    public int seconds(int phase) {
        return switch (phase) { case 0 -> dawn; case 1 -> day; case 2 -> dusk; default -> night; };
    }
}
