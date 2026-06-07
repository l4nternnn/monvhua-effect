package com.kuilunfuzhe.monvhua.features.gravity;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;

public final class InvertedBlockTextureVertexConsumer implements VertexConsumer {
    private static final int VERTEX_STRIDE = 8;
    private static final int Y_OFFSET = 1;
    private static final int U_OFFSET = 4;
    private static final int V_OFFSET = 5;
    private static final int[] REVERSED_WINDING = {0, 3, 2, 1};

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
        delegate.vertex(x, 1.0F - y, z, color, u, v, overlay, light, normalX, -normalY, normalZ);
    }

    @Override
    public void quad(MatrixStack.Entry matrixEntry, BakedQuad quad, float red, float green, float blue, float alpha, int light, int overlay) {
        delegate.quad(matrixEntry, mirrorVertically(quad), red, green, blue, alpha, light, overlay);
    }

    @Override
    public void quad(MatrixStack.Entry matrixEntry, BakedQuad quad, float[] brightnesses, float red, float green, float blue, float alpha, int[] lights, int overlay, boolean useQuadColorData) {
        delegate.quad(matrixEntry, mirrorVertically(quad), brightnesses, red, green, blue, alpha, lights, overlay, useQuadColorData);
    }

    private static BakedQuad mirrorVertically(BakedQuad quad) {
        int[] source = quad.vertexData();
        int[] data = source.clone();
        int vertices = data.length / VERTEX_STRIDE;
        boolean horizontalFace = quad.face() != null && quad.face().getAxis() == Direction.Axis.Y;
        float minU = Float.POSITIVE_INFINITY;
        float maxU = Float.NEGATIVE_INFINITY;
        float minV = Float.POSITIVE_INFINITY;
        float maxV = Float.NEGATIVE_INFINITY;

        for (int vertex = 0; vertex < vertices; vertex++) {
            float u = Float.intBitsToFloat(source[vertex * VERTEX_STRIDE + U_OFFSET]);
            float v = Float.intBitsToFloat(source[vertex * VERTEX_STRIDE + V_OFFSET]);
            minU = Math.min(minU, u);
            maxU = Math.max(maxU, u);
            minV = Math.min(minV, v);
            maxV = Math.max(maxV, v);
        }

        if (!Float.isFinite(minU) || !Float.isFinite(maxU) || !Float.isFinite(minV) || !Float.isFinite(maxV)) {
            return quad;
        }

        float sumU = minU + maxU;
        float sumV = minV + maxV;
        for (int vertex = 0; vertex < vertices; vertex++) {
            int sourceVertex = REVERSED_WINDING[vertex];
            int sourceBase = sourceVertex * VERTEX_STRIDE;
            int targetBase = vertex * VERTEX_STRIDE;
            System.arraycopy(source, sourceBase, data, targetBase, VERTEX_STRIDE);

            float y = Float.intBitsToFloat(source[sourceBase + Y_OFFSET]);
            float u = Float.intBitsToFloat(source[sourceBase + U_OFFSET]);
            float v = Float.intBitsToFloat(source[sourceBase + V_OFFSET]);
            data[targetBase + Y_OFFSET] = Float.floatToRawIntBits(1.0F - y);
            data[targetBase + U_OFFSET] = Float.floatToRawIntBits(horizontalFace ? sumU - u : u);
            data[targetBase + V_OFFSET] = Float.floatToRawIntBits(horizontalFace ? sumV - v : v);
        }

        Direction face = flipVertical(quad.face());
        return new BakedQuad(data, quad.tintIndex(), face, quad.sprite(), quad.shade(), quad.lightEmission());
    }

    private static Direction flipVertical(Direction direction) {
        if (direction == Direction.UP) {
            return Direction.DOWN;
        }
        if (direction == Direction.DOWN) {
            return Direction.UP;
        }
        return direction;
    }
}
