package com.kuilunfuzhe.monvhua.features.activity;

/** Server-selected outline treatment for regular activity bubbles. */
public enum UiActivityBubbleStyle {
    DEFAULT(255),
    PIXEL(254);

    private final int vertexAlpha;

    UiActivityBubbleStyle(int vertexAlpha) {
        this.vertexAlpha = vertexAlpha;
    }

    public int vertexAlpha() {
        return vertexAlpha;
    }

    public static UiActivityBubbleStyle fromId(int id) {
        return id == PIXEL.ordinal() ? PIXEL : DEFAULT;
    }
}
