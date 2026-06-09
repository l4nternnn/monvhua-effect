package com.kuilunfuzhe.monvhua.features.gravity;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;

public final class InvertedBedVertexConsumerProvider implements VertexConsumerProvider {
    private final VertexConsumerProvider delegate;

    public InvertedBedVertexConsumerProvider(VertexConsumerProvider delegate) {
        this.delegate = delegate;
    }

    @Override
    public VertexConsumer getBuffer(RenderLayer layer) {
        return new InvertedBedVertexConsumer(delegate.getBuffer(layer));
    }
}
