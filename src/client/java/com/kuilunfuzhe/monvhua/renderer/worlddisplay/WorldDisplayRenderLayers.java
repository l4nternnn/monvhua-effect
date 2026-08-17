package com.kuilunfuzhe.monvhua.renderer.worlddisplay;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

final class WorldDisplayRenderLayers {
    private static final Map<Identifier, RenderLayer> CHEST_LAYERS = new HashMap<>();

    private WorldDisplayRenderLayers() {
    }

    static RenderLayer chest(Identifier atlas) {
        return CHEST_LAYERS.computeIfAbsent(atlas, WorldDisplayRenderLayers::createChest);
    }

    private static RenderLayer createChest(Identifier atlas) {
        return RenderLayer.of(
                "monvhua_world_display_chest_" + Integer.toUnsignedString(atlas.hashCode(), 36),
                RenderLayer.DEFAULT_BUFFER_SIZE,
                false,
                true,
                WorldDisplayRenderPipelines.CHEST,
                RenderLayer.MultiPhaseParameters.builder()
                        .texture(RenderPhase.Textures.create().add(atlas, false).build())
                        .build(false)
        );
    }
}
