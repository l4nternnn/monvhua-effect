package com.kuilunfuzhe.monvhua.features.forensics.physics;

/** Resting clocks stop; controls and terrain changes wake the worker independently of advanceClock. */
public final class CorpseIdlePolicy {
    private CorpseIdlePolicy() {}
    public static boolean resting(CorpsePhysicsBridge.ResidentState state) {
        return state != null && switch (state.phase()) {
            case "SLEEPING", "SUPPORTED", "PINNED" -> true;
            default -> false;
        };
    }
    public static boolean advance(CorpsePhysicsBridge.ResidentState state, boolean held, boolean terrainPending) {
        return state != null && !state.paused() && !state.phase().equals("FAILED") && !state.phase().equals("CLOSED")
                && (held || terrainPending || !resting(state));
    }
}
