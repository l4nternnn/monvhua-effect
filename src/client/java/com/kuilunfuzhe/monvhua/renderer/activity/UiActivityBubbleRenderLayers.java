package com.kuilunfuzhe.monvhua.renderer.activity;

import com.kuilunfuzhe.monvhua.compat.ActivityIrisCompat;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

public final class UiActivityBubbleRenderLayers {
    private static final Identifier DEFAULT_TEXTURE = Identifier.ofVanilla("textures/block/white_concrete.png");
    private static final Identifier MAGIC_FONT_TEXTURE = Identifier.ofVanilla("textures/font/ascii_sga.png");
    private static final RenderPhase.Texturing IRIS_BUBBLE_BYPASS = new RenderPhase.Texturing(
            "monvhua_iris_activity_bubble_bypass",
            ActivityIrisCompat::beginBubbleRender,
            ActivityIrisCompat::endBubbleRender
    );
    private static final Map<Identifier, RenderLayer> LAYERS = new HashMap<>();

    private UiActivityBubbleRenderLayers() {
    }

    public static RenderLayer bubble() {
        return bubble(DEFAULT_TEXTURE);
    }

    public static RenderLayer bubble(Identifier texture) {
        Identifier resolved = texture == null ? DEFAULT_TEXTURE : texture;
        return LAYERS.computeIfAbsent(resolved, UiActivityBubbleRenderLayers::create);
    }

    private static RenderLayer create(Identifier texture) {
        return RenderLayer.of(
                "monvhua_ui_activity_bubble_" + Integer.toUnsignedString(texture.hashCode(), 36),
                RenderLayer.DEFAULT_BUFFER_SIZE,
                false,
                true,
                UiActivityBubblePipelines.BUBBLE,
                RenderLayer.MultiPhaseParameters.builder()
                        .texture(RenderPhase.Textures.create()
                                .add(texture, false)
                                .add(MAGIC_FONT_TEXTURE, false)
                                .build())
                        .texturing(IRIS_BUBBLE_BYPASS)
                        .build(false)
        );
    }
}
