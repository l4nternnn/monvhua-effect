package com.kuilunfuzhe.monvhua.features.dissolve.client;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

final class DissolveRenderLayers {
    private static final Map<Identifier, RenderLayer> EDGE_LAYERS = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Identifier, RenderLayer> eldest) {
            return size() > 64;
        }
    };

    private DissolveRenderLayers() {
    }

    static synchronized RenderLayer edge(Identifier texture) {
        return EDGE_LAYERS.computeIfAbsent(texture, RenderLayer::getEntityTranslucent);
    }
}
