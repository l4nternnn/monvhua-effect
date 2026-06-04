package com.kuilunfuzhe.monvhua.features.cosmic_box;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.block.entity.EndPortalBlockEntityRenderer;
import net.minecraft.util.Identifier;

public final class CosmicBoxRenderLayers {
    private static final Identifier SHADER_USE_TEXTURE = Identifier.of(MonvhuaMod.MOD_ID, "textures/block/shader_use3.png");

    private static final RenderLayer COSMIC_BOX = RenderLayer.of(
            "monvhua_cosmic_box",
            RenderLayer.DEFAULT_BUFFER_SIZE,
            false,
            false,
            CosmicBoxRenderPipelines.COSMIC_BOX,
            RenderLayer.MultiPhaseParameters.builder()
                    .texture(RenderPhase.Textures.create()
                            .add(EndPortalBlockEntityRenderer.SKY_TEXTURE, false)
                            .add(SHADER_USE_TEXTURE, false)
                            .build())
                    .build(false)
    );

    private CosmicBoxRenderLayers() {
    }

    public static RenderLayer cosmicBox() {
        return COSMIC_BOX;
    }
}
