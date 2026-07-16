package com.kuilunfuzhe.monvhua.features.portal.client;

import com.kuilunfuzhe.monvhua.compat.PortalIrisCompat;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PortalRenderLayers {
    private static final int MAX_CACHED_LAYERS = 64;
    private static final RenderPhase.Texturing IRIS_PORTAL_SURFACE_BYPASS = new RenderPhase.Texturing(
            "monvhua_iris_portal_surface_bypass",
            PortalIrisCompat::beginPortalSurfaceRender,
            PortalIrisCompat::endPortalSurfaceRender
    );
    private static final Map<Identifier, RenderLayer> SURFACE_LAYERS =
            new LinkedHashMap<>(16, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Identifier, RenderLayer> eldest) {
                    return size() > MAX_CACHED_LAYERS;
                }
            };

    private PortalRenderLayers() {
    }

    public static synchronized RenderLayer surface(Identifier texture) {
        return SURFACE_LAYERS.computeIfAbsent(texture, PortalRenderLayers::createSurface);
    }

    private static RenderLayer createSurface(Identifier texture) {
        return RenderLayer.of(
                "monvhua_portal_surface_" + Integer.toUnsignedString(texture.hashCode(), 36),
                RenderLayer.DEFAULT_BUFFER_SIZE,
                false,
                true,
                PortalRenderPipelines.PORTAL_SURFACE,
                RenderLayer.MultiPhaseParameters.builder()
                        .texture(RenderPhase.Textures.create().add(texture, false).build())
                        .texturing(IRIS_PORTAL_SURFACE_BYPASS)
                        .build(false)
        );
    }
}
