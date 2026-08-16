package com.kuilunfuzhe.monvhua.renderer.activity;

import net.minecraft.client.render.RenderLayer;

public final class UiActivityBubbleRenderLayers {
    private static final RenderLayer BUBBLE = RenderLayer.of(
            "monvhua_ui_activity_bubble",
            RenderLayer.DEFAULT_BUFFER_SIZE,
            false,
            true,
            UiActivityBubblePipelines.BUBBLE,
            RenderLayer.MultiPhaseParameters.builder().build(false)
    );

    private UiActivityBubbleRenderLayers() {
    }

    public static RenderLayer bubble() {
        return BUBBLE;
    }
}
