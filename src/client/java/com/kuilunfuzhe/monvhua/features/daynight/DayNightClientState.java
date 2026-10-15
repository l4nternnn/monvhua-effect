package com.kuilunfuzhe.monvhua.features.daynight;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;

/** Client-thread prediction only; the server remains the authority. */
public final class DayNightClientState {
    private static DayNightConfig config = DayNightConfig.defaults();
    private static DayNightController clock = new DayNightController();
    private static ClientWorld lastWorld;
    private static boolean ready, paused;
    public static void update(DayNightPackets.State packet) {
        DayNightConfig next=packet.config(); ready=packet.bridgeReady(); paused=packet.paused();
        if (!next.equals(config)) clock = new DayNightController();
        config = next;
    }
    public static void clear() {
        config = DayNightConfig.defaults(); clock = new DayNightController(); lastWorld = null; ready=false; paused=false;
    }
    public static long nextTime(ClientWorld world, long vanilla) {
        if (!config.enabled() || !world.getRegistryKey().equals(World.OVERWORLD)) return vanilla;
        if (!ready || paused) return world.getTimeOfDay();
        if (world != lastWorld) { clock = new DayNightController(); lastWorld = world; }
        return clock.advance(world.getTimeOfDay(), config);
    }
}
