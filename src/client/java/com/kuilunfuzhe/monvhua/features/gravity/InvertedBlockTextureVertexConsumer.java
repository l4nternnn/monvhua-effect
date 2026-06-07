package com.kuilunfuzhe.monvhua.features.gravity;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;

public final class InvertedBlockTextureVertexConsumer implements VertexConsumer {
    private static final int VERTEX_STRIDE = 8;
    private static final int U_OFFSET = 4;
    private static final int V_OFFSET = 5;

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
        delegate.vertex(x, y, z, color, u, v, overlay, light, normalX, normalY, normalZ);
    }

    @Override
    public void quad(MatrixStack.Entry matrixEntry, BakedQuad quad, float red, float green, float blue, float alpha, int light, int overlay) {
        delegate.quad(matrixEntry, flipTextureVertically(quad), red, green, blue, alpha, light, overlay);
    }

    @Override
    public void quad(MatrixStack.Entry matrixEntry, BakedQuad quad, float[] brightnesses, float red, float green, float blue, float alpha, int[] lights, int overlay, boolean useQuadColorData) {
        delegate.quad(matrixEntry, flipTextureVertically(quad), brightnesses, red, green, blue, alpha, lights, overlay, useQuadColorData);
    }

    private static BakedQuad flipTextureVertically(BakedQuad quad) {
        int[] source = quad.vertexData();
        int[] data = source.clone();
        int vertices = data.length / VERTEX_STRIDE;
        float minV = Float.POSITIVE_INFINITY;
        float maxV = Float.NEGATIVE_INFINITY;

        for (int vertex = 0; vertex < vertices; vertex++) {
            float v = Float.intBitsToFloat(source[vertex * VERTEX_STRIDE + V_OFFSET]);
            minV = Math.min(minV, v);
            maxV = Math.max(maxV, v);
        }

        if (!Float.isFinite(minV) || !Float.isFinite(maxV)) {
            return quad;
        }

        float sumV = minV + maxV;
        for (int vertex = 0; vertex < vertices; vertex++) {
            int vIndex = vertex * VERTEX_STRIDE + V_OFFSET;
            float v = Float.intBitsToFloat(source[vIndex]);
            data[vIndex] = Float.floatToRawIntBits(sumV - v);
        }

        Direction face = quad.face();
        return new BakedQuad(data, quad.tintIndex(), face, quad.sprite(), quad.shade(), quad.lightEmission());
    }
}
