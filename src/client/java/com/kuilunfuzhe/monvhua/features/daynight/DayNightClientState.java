package com.kuilunfuzhe.monvhua.features.daynight;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;

/** Client-thread prediction only; the server remains the authority. */
public final class DayNightClientState {
    private static DayNightConfig config = DayNightConfig.defaults();
    private static DayNightController clock = new DayNightController();
    private static ClientWorld lastWorld;
    public static void update(DayNightConfig next) {
        if (!next.equals(config)) clock = new DayNightController();
        config = next;
    }
    public static void clear() {
        config = DayNightConfig.defaults(); clock = new DayNightController(); lastWorld = null;
    }
    public static long nextTime(ClientWorld world, long vanilla) {
        if (!config.enabled() || !world.getRegistryKey().equals(World.OVERWORLD)) return vanilla;
        if (world != lastWorld) { clock = new DayNightController(); lastWorld = world; }
        return clock.advance(world.getTimeOfDay(), config);
    }
}
