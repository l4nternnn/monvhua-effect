package com.kuilunfuzhe.monvhua.features.gravity;

import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.util.math.Direction;

public final class InvertedBlockQuadTransform {
    private static final int VERTEX_STRIDE = 8;
    private static final int Y_OFFSET = 1;
    private static final int U_OFFSET = 4;
    private static final int V_OFFSET = 5;
    private static final int[] REVERSED_WINDING = {0, 3, 2, 1};

    private InvertedBlockQuadTransform() {
    }

    public static float mirrorY(float y) {
        return 1.0F - y;
    }

    public static float mirrorNormalY(float normalY) {
        return -normalY;
    }

    public static BakedQuad mirror(BakedQuad quad) {
        int[] source = quad.vertexData();
        int[] data = source.clone();
        int vertices = data.length / VERTEX_STRIDE;
        if (vertices != 4) {
            return quad;
        }

        UvBounds uv = UvBounds.read(source, vertices);
        if (!uv.isValid()) {
            return quad;
        }

        boolean featureFace = isFeatureFace(quad.face());
        for (int vertex = 0; vertex < vertices; vertex++) {
            int sourceVertex = REVERSED_WINDING[vertex];
            int sourceBase = sourceVertex * VERTEX_STRIDE;
            int targetBase = vertex * VERTEX_STRIDE;
            System.arraycopy(source, sourceBase, data, targetBase, VERTEX_STRIDE);

            float y = Float.intBitsToFloat(source[sourceBase + Y_OFFSET]);
            float u = Float.intBitsToFloat(source[sourceBase + U_OFFSET]);
            float v = Float.intBitsToFloat(source[sourceBase + V_OFFSET]);
            data[targetBase + Y_OFFSET] = Float.floatToRawIntBits(mirrorY(y));
            data[targetBase + U_OFFSET] = Float.floatToRawIntBits(featureFace ? uv.sumU() - u : u);
            data[targetBase + V_OFFSET] = Float.floatToRawIntBits(featureFace ? uv.sumV() - v : v);
        }

        return new BakedQuad(data, quad.tintIndex(), flipVertical(quad.face()), quad.sprite(), quad.shade(), quad.lightEmission());
    }

    public static void mirror(MutableQuadView quad, float[] brightnesses) {
        Direction originalFace = quadFace(quad);
        boolean featureFace = isFeatureFace(originalFace);
        UvBounds uv = UvBounds.read(quad);
        if (!uv.isValid()) {
            return;
        }

        float[] x = new float[4];
        float[] y = new float[4];
        float[] z = new float[4];
        float[] u = new float[4];
        float[] v = new float[4];
        int[] color = new int[4];
        int[] light = new int[4];
        boolean[] hasNormal = new boolean[4];
        float[] normalX = new float[4];
        float[] normalY = new float[4];
        float[] normalZ = new float[4];
        float[] brightness = brightnesses.clone();

        for (int vertex = 0; vertex < 4; vertex++) {
            x[vertex] = quad.x(vertex);
            y[vertex] = mirrorY(quad.y(vertex));
            z[vertex] = quad.z(vertex);
            u[vertex] = featureFace ? uv.sumU() - quad.u(vertex) : quad.u(vertex);
            v[vertex] = featureFace ? uv.sumV() - quad.v(vertex) : quad.v(vertex);
            color[vertex] = quad.color(vertex);
            light[vertex] = quad.lightmap(vertex);
            hasNormal[vertex] = quad.hasNormal(vertex);
            normalX[vertex] = hasNormal[vertex] ? quad.normalX(vertex) : 0.0F;
            normalY[vertex] = hasNormal[vertex] ? mirrorNormalY(quad.normalY(vertex)) : 0.0F;
            normalZ[vertex] = hasNormal[vertex] ? quad.normalZ(vertex) : 0.0F;
        }

        for (int vertex = 0; vertex < 4; vertex++) {
            int source = REVERSED_WINDING[vertex];
            quad.pos(vertex, x[source], y[source], z[source]);
            quad.uv(vertex, u[source], v[source]);
            quad.color(vertex, color[source]);
            quad.lightmap(vertex, light[source]);
            brightnesses[vertex] = brightness[source];
            if (hasNormal[source]) {
                quad.normal(vertex, normalX[source], normalY[source], normalZ[source]);
            }
        }

        if (quad.cullFace() != null) {
            quad.cullFace(flipVertical(quad.cullFace()));
        }
        if (quad.nominalFace() != null) {
            quad.nominalFace(flipVertical(quad.nominalFace()));
        }
    }

    public static Direction flipVertical(Direction direction) {
        if (direction == Direction.UP) {
            return Direction.DOWN;
        }
        if (direction == Direction.DOWN) {
            return Direction.UP;
        }
        return direction;
    }

    private static boolean isFeatureFace(Direction face) {
        return face != null && face.getAxis() == Direction.Axis.Y;
    }

    private static Direction quadFace(MutableQuadView quad) {
        if (quad.cullFace() != null) {
            return quad.cullFace();
        }
        if (quad.nominalFace() != null) {
            return quad.nominalFace();
        }
        return quad.lightFace();
    }

    private record UvBounds(float minU, float maxU, float minV, float maxV) {
        private static UvBounds read(int[] vertexData, int vertices) {
            float minU = Float.POSITIVE_INFINITY;
            float maxU = Float.NEGATIVE_INFINITY;
            float minV = Float.POSITIVE_INFINITY;
            float maxV = Float.NEGATIVE_INFINITY;

            for (int vertex = 0; vertex < vertices; vertex++) {
                float u = Float.intBitsToFloat(vertexData[vertex * VERTEX_STRIDE + U_OFFSET]);
                float v = Float.intBitsToFloat(vertexData[vertex * VERTEX_STRIDE + V_OFFSET]);
                minU = Math.min(minU, u);
                maxU = Math.max(maxU, u);
                minV = Math.min(minV, v);
                maxV = Math.max(maxV, v);
            }

            return new UvBounds(minU, maxU, minV, maxV);
        }

        private static UvBounds read(MutableQuadView quad) {
            float minU = Float.POSITIVE_INFINITY;
            float maxU = Float.NEGATIVE_INFINITY;
            float minV = Float.POSITIVE_INFINITY;
            float maxV = Float.NEGATIVE_INFINITY;

            for (int vertex = 0; vertex < 4; vertex++) {
                float u = quad.u(vertex);
                float v = quad.v(vertex);
                minU = Math.min(minU, u);
                maxU = Math.max(maxU, u);
                minV = Math.min(minV, v);
                maxV = Math.max(maxV, v);
            }

            return new UvBounds(minU, maxU, minV, maxV);
        }

        private boolean isValid() {
            return Float.isFinite(minU)
                    && Float.isFinite(maxU)
                    && Float.isFinite(minV)
                    && Float.isFinite(maxV);
        }

        private float sumU() {
            return minU + maxU;
        }

        private float sumV() {
            return minV + maxV;
        }
    }
}
