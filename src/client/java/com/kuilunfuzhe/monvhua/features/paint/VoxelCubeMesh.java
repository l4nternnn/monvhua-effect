package com.kuilunfuzhe.monvhua.features.paint;

import java.util.LinkedHashMap;
import java.util.Map;

/** Cached merged surface mesh for the paint target preview's odd-diameter voxel spheroid. */
final class VoxelCubeMesh {
    private static final int MAX_CACHED_MESHES = 8;
    private static final Map<Integer, VoxelCubeMesh> CACHE = new LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, VoxelCubeMesh> eldest) {
            return size() > MAX_CACHED_MESHES;
        }
    };
    private static final int FACE_POSITIVE_X = 0;
    private static final int FACE_NEGATIVE_X = 1;
    private static final int FACE_POSITIVE_Y = 2;
    private static final int FACE_NEGATIVE_Y = 3;
    private static final int FACE_POSITIVE_Z = 4;
    private static final int FACE_NEGATIVE_Z = 5;

    private final int diameterPixels;
    private final float[] positions;
    private final float[] normals;

    private VoxelCubeMesh(int diameterPixels, float[] positions, float[] normals) {
        this.diameterPixels = diameterPixels;
        this.positions = positions;
        this.normals = normals;
    }

    static VoxelCubeMesh forRadius(int radius) {
        int diameterPixels = Math.max(1, radius * 2 - 1);
        synchronized (CACHE) {
            return CACHE.computeIfAbsent(diameterPixels, VoxelCubeMesh::create);
        }
    }

    int diameterPixels() {
        return diameterPixels;
    }

    int vertexCount() {
        return positions.length / 3;
    }

    float position(int index) {
        return positions[index];
    }

    float normal(int index) {
        return normals[index];
    }

    private static VoxelCubeMesh create(int diameterPixels) {
        boolean[] solid = createSolidVoxels(diameterPixels);
        MeshBuilder builder = new MeshBuilder(Math.max(36, diameterPixels * diameterPixels));
        appendMergedFaces(builder, solid, diameterPixels, FACE_POSITIVE_X);
        appendMergedFaces(builder, solid, diameterPixels, FACE_NEGATIVE_X);
        appendMergedFaces(builder, solid, diameterPixels, FACE_POSITIVE_Y);
        appendMergedFaces(builder, solid, diameterPixels, FACE_NEGATIVE_Y);
        appendMergedFaces(builder, solid, diameterPixels, FACE_POSITIVE_Z);
        appendMergedFaces(builder, solid, diameterPixels, FACE_NEGATIVE_Z);
        return new VoxelCubeMesh(diameterPixels, builder.positions(), builder.normals());
    }

    private static boolean[] createSolidVoxels(int diameterPixels) {
        boolean[] solid = new boolean[diameterPixels * diameterPixels * diameterPixels];
        int center = diameterPixels / 2;
        double radius = diameterPixels * 0.5D;
        double radiusSquared = radius * radius;
        for (int z = 0; z < diameterPixels; z++) {
            int dz = z - center;
            for (int y = 0; y < diameterPixels; y++) {
                int dy = y - center;
                for (int x = 0; x < diameterPixels; x++) {
                    int dx = x - center;
                    solid[index(x, y, z, diameterPixels)] = dx * dx + dy * dy + dz * dz <= radiusSquared;
                }
            }
        }
        return solid;
    }

    private static void appendMergedFaces(MeshBuilder builder, boolean[] solid, int diameterPixels, int face) {
        boolean[] mask = new boolean[diameterPixels * diameterPixels];
        for (int slice = 0; slice < diameterPixels; slice++) {
            fillFaceMask(mask, solid, diameterPixels, face, slice);
            appendGreedyMask(builder, mask, diameterPixels, face, slice);
        }
    }

    private static void fillFaceMask(boolean[] mask, boolean[] solid, int diameterPixels, int face, int slice) {
        for (int v = 0; v < diameterPixels; v++) {
            for (int u = 0; u < diameterPixels; u++) {
                mask[v * diameterPixels + u] = switch (face) {
                    case FACE_POSITIVE_X -> isSolid(solid, slice, u, v, diameterPixels)
                            && !isSolid(solid, slice + 1, u, v, diameterPixels);
                    case FACE_NEGATIVE_X -> isSolid(solid, slice, u, v, diameterPixels)
                            && !isSolid(solid, slice - 1, u, v, diameterPixels);
                    case FACE_POSITIVE_Y -> isSolid(solid, u, slice, v, diameterPixels)
                            && !isSolid(solid, u, slice + 1, v, diameterPixels);
                    case FACE_NEGATIVE_Y -> isSolid(solid, u, slice, v, diameterPixels)
                            && !isSolid(solid, u, slice - 1, v, diameterPixels);
                    case FACE_POSITIVE_Z -> isSolid(solid, u, v, slice, diameterPixels)
                            && !isSolid(solid, u, v, slice + 1, diameterPixels);
                    case FACE_NEGATIVE_Z -> isSolid(solid, u, v, slice, diameterPixels)
                            && !isSolid(solid, u, v, slice - 1, diameterPixels);
                    default -> false;
                };
            }
        }
    }

    private static void appendGreedyMask(MeshBuilder builder, boolean[] mask, int diameterPixels, int face, int slice) {
        for (int v = 0; v < diameterPixels; v++) {
            for (int u = 0; u < diameterPixels; u++) {
                int start = v * diameterPixels + u;
                if (!mask[start]) {
                    continue;
                }
                int width = 1;
                while (u + width < diameterPixels && mask[start + width]) {
                    width++;
                }
                int height = 1;
                while (v + height < diameterPixels && rowFilled(mask, diameterPixels, u, v + height, width)) {
                    height++;
                }
                clearMask(mask, diameterPixels, u, v, width, height);
                appendFace(builder, face, slice, u, v, width, height, diameterPixels);
            }
        }
    }

    private static boolean rowFilled(boolean[] mask, int diameterPixels, int u, int v, int width) {
        int start = v * diameterPixels + u;
        for (int offset = 0; offset < width; offset++) {
            if (!mask[start + offset]) {
                return false;
            }
        }
        return true;
    }

    private static void clearMask(boolean[] mask, int diameterPixels, int u, int v, int width, int height) {
        for (int y = v; y < v + height; y++) {
            int start = y * diameterPixels + u;
            for (int x = 0; x < width; x++) {
                mask[start + x] = false;
            }
        }
    }

    private static void appendFace(MeshBuilder builder, int face, int slice, int u, int v,
                                   int width, int height, int diameterPixels) {
        float half = diameterPixels * 0.5F;
        float planeMin = slice - half;
        float planeMax = slice + 1.0F - half;
        float minU = u - half;
        float maxU = u + width - half;
        float minV = v - half;
        float maxV = v + height - half;
        switch (face) {
            case FACE_POSITIVE_X -> appendPositiveX(builder, planeMax, minU, maxU, minV, maxV);
            case FACE_NEGATIVE_X -> appendNegativeX(builder, planeMin, minU, maxU, minV, maxV);
            case FACE_POSITIVE_Y -> appendPositiveY(builder, planeMax, minU, maxU, minV, maxV);
            case FACE_NEGATIVE_Y -> appendNegativeY(builder, planeMin, minU, maxU, minV, maxV);
            case FACE_POSITIVE_Z -> appendPositiveZ(builder, planeMax, minU, maxU, minV, maxV);
            case FACE_NEGATIVE_Z -> appendNegativeZ(builder, planeMin, minU, maxU, minV, maxV);
            default -> {
            }
        }
    }

    private static void appendPositiveX(MeshBuilder builder, float x, float minY, float maxY, float minZ, float maxZ) {
        appendQuad(builder, x, minY, maxZ, x, minY, minZ, x, maxY, minZ, x, maxY, maxZ, 1.0F, 0.0F, 0.0F);
    }

    private static void appendNegativeX(MeshBuilder builder, float x, float minY, float maxY, float minZ, float maxZ) {
        appendQuad(builder, x, minY, minZ, x, minY, maxZ, x, maxY, maxZ, x, maxY, minZ, -1.0F, 0.0F, 0.0F);
    }

    private static void appendPositiveY(MeshBuilder builder, float y, float minX, float maxX, float minZ, float maxZ) {
        appendQuad(builder, minX, y, maxZ, maxX, y, maxZ, maxX, y, minZ, minX, y, minZ, 0.0F, 1.0F, 0.0F);
    }

    private static void appendNegativeY(MeshBuilder builder, float y, float minX, float maxX, float minZ, float maxZ) {
        appendQuad(builder, minX, y, minZ, maxX, y, minZ, maxX, y, maxZ, minX, y, maxZ, 0.0F, -1.0F, 0.0F);
    }

    private static void appendPositiveZ(MeshBuilder builder, float z, float minX, float maxX, float minY, float maxY) {
        appendQuad(builder, minX, minY, z, maxX, minY, z, maxX, maxY, z, minX, maxY, z, 0.0F, 0.0F, 1.0F);
    }

    private static void appendNegativeZ(MeshBuilder builder, float z, float minX, float maxX, float minY, float maxY) {
        appendQuad(builder, maxX, minY, z, minX, minY, z, minX, maxY, z, maxX, maxY, z, 0.0F, 0.0F, -1.0F);
    }

    private static void appendQuad(MeshBuilder builder,
                                   float ax, float ay, float az,
                                   float bx, float by, float bz,
                                   float cx, float cy, float cz,
                                   float dx, float dy, float dz,
                                   float normalX, float normalY, float normalZ) {
        builder.vertex(ax, ay, az, normalX, normalY, normalZ);
        builder.vertex(bx, by, bz, normalX, normalY, normalZ);
        builder.vertex(cx, cy, cz, normalX, normalY, normalZ);
        builder.vertex(dx, dy, dz, normalX, normalY, normalZ);
    }

    private static boolean isSolid(boolean[] solid, int x, int y, int z, int diameterPixels) {
        if (x < 0 || y < 0 || z < 0 || x >= diameterPixels || y >= diameterPixels || z >= diameterPixels) {
            return false;
        }
        return solid[index(x, y, z, diameterPixels)];
    }

    private static int index(int x, int y, int z, int diameterPixels) {
        return (z * diameterPixels + y) * diameterPixels + x;
    }

    private static final class MeshBuilder {
        private float[] positions;
        private float[] normals;
        private int vertexIndex;

        private MeshBuilder(int vertexCapacity) {
            this.positions = new float[vertexCapacity * 3];
            this.normals = new float[vertexCapacity * 3];
        }

        private void vertex(float x, float y, float z, float normalX, float normalY, float normalZ) {
            ensureCapacity(vertexIndex + 1);
            int index = vertexIndex * 3;
            positions[index] = x;
            positions[index + 1] = y;
            positions[index + 2] = z;
            normals[index] = normalX;
            normals[index + 1] = normalY;
            normals[index + 2] = normalZ;
            vertexIndex++;
        }

        private void ensureCapacity(int requiredVertices) {
            int currentVertices = positions.length / 3;
            if (requiredVertices <= currentVertices) {
                return;
            }
            int nextVertices = Math.max(requiredVertices, currentVertices * 2);
            float[] nextPositions = new float[nextVertices * 3];
            float[] nextNormals = new float[nextVertices * 3];
            System.arraycopy(positions, 0, nextPositions, 0, vertexIndex * 3);
            System.arraycopy(normals, 0, nextNormals, 0, vertexIndex * 3);
            positions = nextPositions;
            normals = nextNormals;
        }

        private float[] positions() {
            float[] result = new float[vertexIndex * 3];
            System.arraycopy(positions, 0, result, 0, result.length);
            return result;
        }

        private float[] normals() {
            float[] result = new float[vertexIndex * 3];
            System.arraycopy(normals, 0, result, 0, result.length);
            return result;
        }
    }
}
