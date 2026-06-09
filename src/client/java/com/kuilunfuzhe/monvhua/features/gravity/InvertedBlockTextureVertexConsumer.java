package com.kuilunfuzhe.monvhua.features.gravity;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.util.math.MatrixStack;

public final class InvertedBlockTextureVertexConsumer implements VertexConsumer {
    private final VertexConsumer delegate;

    public InvertedBlockTextureVertexConsumer(VertexConsumer delegate) {
        this.delegate = delegate;
    }

    @Override
    public VertexConsumer vertex(float x, float y, float z) {
        delegate.vertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        delegate.color(red, green, blue, alpha);
        return this;
    }

    @Override
    public VertexConsumer texture(float u, float v) {
        delegate.texture(u, v);
        return this;
    }

    @Override
    public VertexConsumer overlay(int u, int v) {
        delegate.overlay(u, v);
        return this;
    }

    @Override
    public VertexConsumer light(int u, int v) {
        delegate.light(u, v);
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        delegate.normal(x, y, z);
        return this;
    }

    @Override
    public void vertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float normalX, float normalY, float normalZ) {
        delegate.vertex(x, InvertedBlockQuadTransform.mirrorY(y), z, color, u, v, overlay, light, normalX, InvertedBlockQuadTransform.mirrorNormalY(normalY), normalZ);
    }

    @Override
    public void quad(MatrixStack.Entry matrixEntry, BakedQuad quad, float red, float green, float blue, float alpha, int light, int overlay) {
        delegate.quad(matrixEntry, InvertedBlockQuadTransform.mirror(quad), red, green, blue, alpha, light, overlay);
    }

    @Override
    public void quad(MatrixStack.Entry matrixEntry, BakedQuad quad, float[] brightnesses, float red, float green, float blue, float alpha, int[] lights, int overlay, boolean useQuadColorData) {
        delegate.quad(matrixEntry, InvertedBlockQuadTransform.mirror(quad), brightnesses, red, green, blue, alpha, lights, overlay, useQuadColorData);
    }
}
