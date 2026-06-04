package com.kuilunfuzhe.monvhua.features.cosmic_box;

public final class CosmicBoxClientState {
    private static boolean canSeeBeam;

    private CosmicBoxClientState() {
    }

    public static boolean canSeeBeam() {
        return canSeeBeam;
    }

    public static void setCanSeeBeam(boolean canSeeBeam) {
        CosmicBoxClientState.canSeeBeam = canSeeBeam;
    }
}
