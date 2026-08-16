package com.kuilunfuzhe.monvhua.features.activity;

public final class UiActivityBubbleSize {
    public static final float DEFAULT_MULTIPLIER = 2.0F;
    public static final float MIN_MULTIPLIER = 0.25F;
    public static final float MAX_MULTIPLIER = 4.0F;

    private UiActivityBubbleSize() {
    }

    public static float sanitize(float multiplier) {
        if (!Float.isFinite(multiplier)) {
            return DEFAULT_MULTIPLIER;
        }
        return Math.clamp(multiplier, MIN_MULTIPLIER, MAX_MULTIPLIER);
    }
}
