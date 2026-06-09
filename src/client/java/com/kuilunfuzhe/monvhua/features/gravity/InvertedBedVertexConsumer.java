package com.kuilunfuzhe.monvhua.features.gravity;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.math.ColorHelper;

public final class InvertedBedVertexConsumer implements VertexConsumer {
    private static final int QUAD_VERTICES = 4;
    private static final int[] REVERSED_WINDING = {0, 3, 2, 1};

    private final VertexConsumer delegate;
    private final BedVertex[] quad = new BedVertex[QUAD_VERTICES];
    private int quadVertexCount;

    private float x;
    private float y;
    private float z;
    private int color = -1;
    private float u;
    private float v;
    private int overlay;
    private int light;
    private boolean buildingVertex;

    public InvertedBedVertexConsumer(VertexConsumer delegate) {
        this.delegate = delegate;
    }

    @Override
    public VertexConsumer vertex(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.buildingVertex = true;
        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        this.color = ColorHelper.getArgb(alpha, red, green, blue);
        return this;
    }

    @Override
    public VertexConsumer texture(float u, float v) {
        this.u = u;
        this.v = v;
        return this;
    }

    @Override
    public VertexConsumer overlay(int u, int v) {
        this.overlay = pack(u, v);
        return this;
    }

    @Override
    public VertexConsumer light(int u, int v) {
        this.light = pack(u, v);
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        if (buildingVertex) {
            buffer(new BedVertex(this.x, this.y, this.z, color, u, v, overlay, light,
                    x, y, z));
            buildingVertex = false;
            return this;
        }

        delegate.normal(x, y, z);
        return this;
    }

    @Override
    public void vertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float normalX, float normalY, float normalZ) {
        buffer(new BedVertex(x, y, z, color, u, v, overlay, light, normalX, normalY, normalZ));
    }

    private void buffer(BedVertex vertex) {
        quad[quadVertexCount++] = vertex;
        if (quadVertexCount == QUAD_VERTICES) {
            flushQuad();
        }
    }

    private void flushQuad() {
        for (int source : REVERSED_WINDING) {
            BedVertex vertex = quad[source];
            delegate.vertex(vertex.x, vertex.y, vertex.z, vertex.color, vertex.u, vertex.v, vertex.overlay, vertex.light,
                    vertex.normalX, vertex.normalY, vertex.normalZ);
            quad[source] = null;
        }
        quadVertexCount = 0;
    }

    private static int pack(int low, int high) {
        return low & 0xFFFF | (high & 0xFFFF) << 16;
    }

    private record BedVertex(float x, float y, float z, int color, float u, float v, int overlay, int light,
                             float normalX, float normalY, float normalZ) {
    }
}
