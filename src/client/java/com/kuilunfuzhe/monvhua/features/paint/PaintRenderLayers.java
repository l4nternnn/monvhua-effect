package com.kuilunfuzhe.monvhua.features.paint;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;

public final class PaintRenderLayers {
    private static final RenderLayer PAINT_OVERLAY = RenderLayer.of(
            "monvhua_paint_overlay",
            RenderLayer.DEFAULT_BUFFER_SIZE,
            false,
            true,
            PaintRenderPipelines.PAINT_OVERLAY,
            RenderLayer.MultiPhaseParameters.builder()
                    .texture(RenderPhase.NO_TEXTURE)
                    .target(RenderPhase.MAIN_TARGET)
                    .build(false)
    );

    private PaintRenderLayers() {
    }

    public static RenderLayer paintOverlay() {
        return PAINT_OVERLAY;
    }
}
