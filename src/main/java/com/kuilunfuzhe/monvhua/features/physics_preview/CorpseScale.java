package com.kuilunfuzhe.monvhua.features.physics_preview;

/** Whole-body scale supported by the six-part physics core. World translations are never scaled twice. */
public final class CorpseScale {
    private CorpseScale() {}
    public static boolean supported(float scale) { return Float.isFinite(scale) && scale >= .5f && scale <= 2f; }
    public static float require(float scale) {
        if (!supported(scale)) throw new IllegalArgumentException("遗体物理仅支持 0.5–2.0 整体倍率：" + scale);
        return scale;
    }
}
