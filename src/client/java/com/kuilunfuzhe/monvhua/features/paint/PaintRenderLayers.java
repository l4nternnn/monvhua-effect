package com.kuilunfuzhe.monvhua.features.paint;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;

public final class PaintRenderLayers {
    private static final Identifier WHITE_TEXTURE = Identifier.ofVanilla("textures/block/white_concrete.png");
    private static final RenderLayer PAINT_OVERLAY = RenderLayer.getEntityTranslucent(WHITE_TEXTURE);

    private PaintRenderLayers() {
    }

    public static RenderLayer paintOverlay() {
        return PAINT_OVERLAY;
    }
}
