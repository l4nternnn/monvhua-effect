package com.kuilunfuzhe.monvhua.features.portal.client.render;

import com.kuilunfuzhe.monvhua.features.portal.PortalItems;
import com.kuilunfuzhe.monvhua.features.portal.PortalBlockEntity;
import com.kuilunfuzhe.monvhua.features.portal.PortalTransform;
import com.kuilunfuzhe.monvhua.features.portal.PortalViewConfig;
import com.kuilunfuzhe.monvhua.features.portal.client.PortalHorizonCache;
import com.kuilunfuzhe.monvhua.features.portal.client.PortalRemoteChunkCache;
import com.kuilunfuzhe.monvhua.features.portal.client.PortalRenderPipelines;
import com.kuilunfuzhe.monvhua.mixin.SpriteContentsAccessor;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.WorldChunk;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public final class IndependentPortalRenderer {
    private static GpuBuffer backgroundBuffer;
    private static int backgroundKey;
    private static int backgroundVertexCount;
    private static GpuBuffer terrainBuffer;
    private static int terrainKey;
    private static int terrainVertexCount;
    private static GpuBuffer chunkMeshBuffer;
    private static int chunkMeshKey;
    private static int chunkMeshVertexCount;
    private static float activeAspect = 1.0F;
    private static ApertureProjection activeAperture;

    private IndependentPortalRenderer() {
    }

    public static boolean render(SimpleFramebuffer targetFramebuffer, Vec3d cameraPos,
                                 float yaw, float pitch, float aspect,
                                 PortalBlockEntity aperturePortal) {
        if (targetFramebuffer == null || targetFramebuffer.getColorAttachmentView() == null) {
            return false;
        }
        activeAspect = MathHelper.clamp(aspect, 0.25F, 4.0F);
        activeAperture = ApertureProjection.from(aperturePortal);

        PortalHorizonCache.HorizonData data = PortalHorizonCache.current();
        int skyColor = data == null ? 0xFF78A7FF : data.skyColor();
        int fogColor = data == null ? 0xFFC8D8EA : data.fogColor();
        int groundColor = data == null ? 0xFF5F8F4E : softenGroundColor(data.averageGroundColor(), fogColor);
        float horizonY = horizonY(data, cameraPos.y, pitch);

        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                targetFramebuffer.getColorAttachment(),
                fogColor,
                targetFramebuffer.getDepthAttachment(),
                1.0
        );

        GpuBuffer buffer = backgroundBuffer(data, skyColor, fogColor, groundColor, horizonY, yaw);
        if (buffer == null) {
            return false;
        }

        try (RenderPass pass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(
                        () -> "Monvhua independent portal background",
                        targetFramebuffer.getColorAttachmentView(),
                        OptionalInt.empty(),
                        targetFramebuffer.getDepthAttachmentView(),
                        OptionalDouble.empty()
                )) {
            pass.setPipeline(PortalRenderPipelines.horizon());
            pass.setVertexBuffer(0, buffer);
            pass.draw(0, backgroundVertexCount);
        }
        renderChunkMesh(targetFramebuffer, cameraPos, yaw, pitch, fogColor);
        return true;
    }

    private static void renderChunkMesh(SimpleFramebuffer targetFramebuffer, Vec3d cameraPos,
                                        float yaw, float pitch, int fogColor) {
        if (!PortalViewConfig.INDEPENDENT_PORTAL_CHUNK_MESH) {
            return;
        }
        GpuBuffer buffer = chunkMeshBuffer(cameraPos, yaw, pitch, fogColor);
        if (buffer == null || chunkMeshVertexCount <= 0) {
            return;
        }
        GpuTextureView blockAtlas = blockAtlasTexture();
        if (blockAtlas == null) {
            return;
        }
        try (RenderPass pass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(
                        () -> "Monvhua independent portal chunk mesh",
                        targetFramebuffer.getColorAttachmentView(),
                        OptionalInt.empty(),
                        targetFramebuffer.getDepthAttachmentView(),
                        OptionalDouble.empty()
                )) {
            pass.setPipeline(PortalRenderPipelines.blockAtlas());
            pass.setVertexBuffer(0, buffer);
            pass.bindSampler("Sampler0", blockAtlas);
            pass.draw(0, chunkMeshVertexCount);
        }
    }

    private static GpuBuffer chunkMeshBuffer(Vec3d cameraPos, float yaw, float pitch, int fogColor) {
        int radius = Math.max(32, PortalViewConfig.INDEPENDENT_PORTAL_CHUNK_MESH_RADIUS_BLOCKS);
        int detailRadius = MathHelper.clamp(
                PortalViewConfig.INDEPENDENT_PORTAL_CHUNK_MESH_DETAIL_RADIUS_BLOCKS,
                8,
                radius
        );
        int nearStep = Math.max(1, PortalViewConfig.INDEPENDENT_PORTAL_CHUNK_MESH_NEAR_STEP_BLOCKS);
        int farStep = Math.max(nearStep, PortalViewConfig.INDEPENDENT_PORTAL_CHUNK_MESH_FAR_STEP_BLOCKS);
        int maxFaces = Math.max(128, PortalViewConfig.INDEPENDENT_PORTAL_CHUNK_MESH_MAX_FACES);
        int key = Long.hashCode(PortalHorizonCache.current() == null ? 0L : PortalHorizonCache.current().generation());
        key = 31 * key + MathHelper.floor(cameraPos.x / nearStep);
        key = 31 * key + MathHelper.floor(cameraPos.y / nearStep);
        key = 31 * key + MathHelper.floor(cameraPos.z / nearStep);
        key = 31 * key + Math.round(yaw * 8.0F);
        key = 31 * key + Math.round(pitch * 8.0F);
        key = 31 * key + fogColor;
        key = 31 * key + PortalRemoteChunkCache.loadedChunkCount();
        key = 31 * key + detailRadius;
        if (chunkMeshBuffer != null && chunkMeshKey == key) {
            return chunkMeshBuffer;
        }
        if (chunkMeshBuffer != null) {
            chunkMeshBuffer.close();
            chunkMeshBuffer = null;
        }

        List<Face> faces = new ArrayList<>(maxFaces);
        PortalRemoteChunkCache.forEachLoadedWorldChunk(chunk -> appendChunkMesh(
                faces,
                maxFaces,
                chunk,
                cameraPos,
                yaw,
                pitch,
                fogColor,
                radius,
                detailRadius,
                nearStep,
                farStep
        ));
        faces.sort(Comparator.comparingDouble(Face::depth).reversed());
        ByteBuffer vertices = ByteBuffer.allocateDirect(faces.size() * 6 * 24).order(ByteOrder.nativeOrder());
        for (Face face : faces) {
            putFace(vertices, face);
        }
        chunkMeshVertexCount = faces.size() * 6;
        if (chunkMeshVertexCount <= 0) {
            return null;
        }
        vertices.flip();
        chunkMeshKey = key;
        chunkMeshBuffer = RenderSystem.getDevice()
                .createBuffer(() -> "Monvhua independent portal chunk mesh", 40, vertices);
        return chunkMeshBuffer;
    }

    private static void appendChunkMesh(List<Face> faces, int maxFaces, WorldChunk chunk,
                                        Vec3d cameraPos, float yaw, float pitch, int fogColor,
                                        int radius, int detailRadius, int nearStep, int farStep) {
        ChunkPos chunkPos = chunk.getPos();
        int minX = chunkPos.getStartX();
        int minZ = chunkPos.getStartZ();
        for (int localZ = 0; localZ < 16 && faces.size() < maxFaces; localZ++) {
            for (int localX = 0; localX < 16 && faces.size() < maxFaces; localX++) {
                int worldX = minX + localX;
                int worldZ = minZ + localZ;
                double distance = Math.max(Math.abs(worldX - cameraPos.x), Math.abs(worldZ - cameraPos.z));
                if (distance > radius) {
                    continue;
                }
                int step = distance <= detailRadius ? 1 : distance > radius * 0.45D ? farStep : nearStep;
                if ((localX % step) != 0 || (localZ % step) != 0) {
                    continue;
                }
                appendTerrainCell(faces, maxFaces, chunk, worldX, worldZ, localX, localZ,
                        cameraPos, yaw, pitch, fogColor, step, radius);
            }
        }
    }

    private static void appendTerrainCell(List<Face> faces, int maxFaces, WorldChunk chunk,
                                          int worldX, int worldZ, int localX, int localZ,
                                          Vec3d cameraPos, float yaw, float pitch, int fogColor,
                                          int step, int radius) {
        if (step <= 1) {
            appendDetailedBlock(faces, maxFaces, chunk, worldX, worldZ, localX, localZ,
                    cameraPos, yaw, pitch, fogColor, radius);
            return;
        }
        int nextLocalX = Math.min(15, localX + step);
        int nextLocalZ = Math.min(15, localZ + step);
        Sample a = sampleTop(chunk, worldX, worldZ, localX, localZ, fogColor);
        Sample b = sampleTop(chunk, worldX + nextLocalX - localX, worldZ, nextLocalX, localZ, fogColor);
        Sample c = sampleTop(chunk, worldX + nextLocalX - localX, worldZ + nextLocalZ - localZ, nextLocalX, nextLocalZ, fogColor);
        Sample d = sampleTop(chunk, worldX, worldZ + nextLocalZ - localZ, localX, nextLocalZ, fogColor);
        if (a.air || b.air || c.air || d.air) {
            return;
        }
        Sprite sprite = topSprite(chunk, a.state, a.pos);
        if (sprite == null) {
            return;
        }
        applyTileUv(sprite, a, b, c, d, worldX, worldZ, Math.max(1, nextLocalX - localX), Math.max(1, nextLocalZ - localZ));
        addWorldFace(faces, maxFaces, cameraPos, yaw, pitch, radius, fogColor,
                a, b, c, d, 0xFFFFFFFF, 1.0F);
    }

    private static void appendDetailedBlock(List<Face> faces, int maxFaces, WorldChunk chunk,
                                            int worldX, int worldZ, int localX, int localZ,
                                            Vec3d cameraPos, float yaw, float pitch, int fogColor,
                                            int radius) {
        int heightY = chunk.sampleHeightmap(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, localX, localZ) - 1;
        int cameraY = MathHelper.floor(cameraPos.y);
        int verticalRadius = MathHelper.clamp(
                PortalViewConfig.INDEPENDENT_PORTAL_CHUNK_MESH_DETAIL_RADIUS_BLOCKS,
                16,
                64
        );
        int minY = MathHelper.clamp(
                Math.min(cameraY - verticalRadius, heightY - PortalViewConfig.INDEPENDENT_PORTAL_CHUNK_MESH_SIDE_DEPTH_BLOCKS),
                chunk.getBottomY(),
                chunk.getTopYInclusive()
        );
        int maxY = MathHelper.clamp(
                Math.max(cameraY + verticalRadius, heightY + 2),
                chunk.getBottomY(),
                chunk.getTopYInclusive()
        );

        for (int y = maxY; y >= minY && faces.size() < maxFaces; y--) {
            BlockPos blockPos = new BlockPos(worldX, y, worldZ);
            BlockState state = chunk.getBlockState(blockPos);
            if (shouldSkipState(state)) {
                continue;
            }
            int fallbackColor = mapColor(state, chunk, blockPos);
            if (!appendBakedModelBlock(faces, maxFaces, chunk, state, blockPos,
                    cameraPos, yaw, pitch, radius, fogColor, worldX, worldZ, localX, localZ, fallbackColor)) {
                appendFallbackCubeBlock(faces, maxFaces, chunk, state, blockPos,
                        cameraPos, yaw, pitch, radius, fogColor);
            }
        }
    }

    private static boolean appendBakedModelBlock(List<Face> faces, int maxFaces, WorldChunk chunk,
                                                 BlockState state, BlockPos pos, Vec3d cameraPos,
                                                 float yaw, float pitch, int radius, int fogColor,
                                                 int worldX, int worldZ, int localX, int localZ,
                                                 int fallbackColor) {
        if (shouldSkipState(state)) {
            return false;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return false;
        }
        int before = faces.size();
        try {
            Random random = Random.create(state.getRenderingSeed(pos));
            for (BlockModelPart part : client.getBlockRenderManager().getModel(state).getParts(random)) {
                for (Direction direction : Direction.values()) {
                    if (isFaceVisible(chunk, pos, direction)) {
                        appendModelQuads(faces, maxFaces, chunk, state, pos, part.getQuads(direction),
                                cameraPos, yaw, pitch, radius, fogColor, fallbackColor, shadeFor(direction));
                    }
                }
                appendModelQuads(faces, maxFaces, chunk, state, pos, part.getQuads(null),
                        cameraPos, yaw, pitch, radius, fogColor, fallbackColor, 1.0F);
            }
        } catch (RuntimeException ignored) {
            return faces.size() > before;
        }
        return faces.size() > before;
    }

    private static void appendVisibleSideQuads(List<Face> faces, int maxFaces, WorldChunk chunk,
                                               BlockState state, BlockPos pos, BlockModelPart part,
                                               Vec3d cameraPos, float yaw, float pitch,
                                               int radius, int fogColor, int fallbackColor,
                                               int worldX, int worldZ, int localX, int localZ) {
        for (Direction direction : Direction.Type.HORIZONTAL) {
            int offsetX = direction.getOffsetX();
            int offsetZ = direction.getOffsetZ();
            float neighborTop = sampleNeighborTop(chunk, worldX + offsetX, worldZ + offsetZ,
                    localX + offsetX, localZ + offsetZ);
            if (!Float.isNaN(neighborTop) && neighborTop >= pos.getY() + 0.92F) {
                continue;
            }
            float shade = switch (direction) {
                case NORTH -> 0.86F;
                case SOUTH -> 0.64F;
                case WEST -> 0.78F;
                case EAST -> 0.70F;
                default -> 1.0F;
            };
            appendModelQuads(faces, maxFaces, chunk, state, pos, part.getQuads(direction),
                    cameraPos, yaw, pitch, radius, fogColor, fallbackColor, shade);
        }
    }

    private static void appendModelQuads(List<Face> faces, int maxFaces, WorldChunk chunk,
                                         BlockState state, BlockPos pos, List<BakedQuad> quads,
                                         Vec3d cameraPos, float yaw, float pitch, int radius,
                                         int fogColor, int fallbackColor, float shade) {
        for (BakedQuad quad : quads) {
            if (faces.size() >= maxFaces) {
                return;
            }
            appendModelQuad(faces, maxFaces, chunk, state, pos, quad,
                    cameraPos, yaw, pitch, radius, fogColor, fallbackColor, shade);
        }
    }

    private static void appendModelQuad(List<Face> faces, int maxFaces, WorldChunk chunk,
                                        BlockState state, BlockPos pos, BakedQuad quad,
                                        Vec3d cameraPos, float yaw, float pitch, int radius,
                                        int fogColor, int fallbackColor, float shade) {
        int[] data = quad.vertexData();
        if (data.length < 32) {
            return;
        }
        QuadVertex a = quadVertex(data, 0, pos);
        QuadVertex b = quadVertex(data, 1, pos);
        QuadVertex c = quadVertex(data, 2, pos);
        QuadVertex d = quadVertex(data, 3, pos);
        int color = quadVertexColor(MinecraftClient.getInstance(), chunk, state, pos, quad.tintIndex());
        addWorldFace(faces, maxFaces, cameraPos, yaw, pitch, radius, fogColor,
                new Sample(a.x, a.y, a.z, color, false, state, pos, a.u, a.v),
                new Sample(b.x, b.y, b.z, color, false, state, pos, b.u, b.v),
                new Sample(c.x, c.y, c.z, color, false, state, pos, c.u, c.v),
                new Sample(d.x, d.y, d.z, color, false, state, pos, d.u, d.v),
                color,
                shade
        );
    }

    private static void appendFallbackCubeBlock(List<Face> faces, int maxFaces, WorldChunk chunk,
                                                BlockState state, BlockPos pos, Vec3d cameraPos,
                                                float yaw, float pitch, int radius, int fogColor) {
        for (Direction direction : Direction.values()) {
            if (faces.size() >= maxFaces) {
                return;
            }
            if (!isFaceVisible(chunk, pos, direction)) {
                continue;
            }
            Sprite sprite = faceSprite(chunk, state, pos, direction);
            if (sprite == null) {
                continue;
            }
            addCubeFace(faces, maxFaces, cameraPos, yaw, pitch, radius, fogColor,
                    state, pos, direction, sprite);
        }
    }

    private static void addCubeFace(List<Face> faces, int maxFaces, Vec3d cameraPos,
                                    float yaw, float pitch, int radius, int fogColor,
                                    BlockState state, BlockPos pos, Direction direction, Sprite sprite) {
        float x0 = pos.getX();
        float x1 = pos.getX() + 1.0F;
        float y0 = pos.getY();
        float y1 = pos.getY() + 1.0F;
        float z0 = pos.getZ();
        float z1 = pos.getZ() + 1.0F;
        int color = 0xFFFFFFFF;
        Sample a;
        Sample b;
        Sample c;
        Sample d;
        switch (direction) {
            case UP -> {
                a = new Sample(x0, y1, z0, color, false, state, pos, 0.0F, 0.0F);
                b = new Sample(x1, y1, z0, color, false, state, pos, 1.0F, 0.0F);
                c = new Sample(x1, y1, z1, color, false, state, pos, 1.0F, 1.0F);
                d = new Sample(x0, y1, z1, color, false, state, pos, 0.0F, 1.0F);
            }
            case DOWN -> {
                a = new Sample(x0, y0, z1, color, false, state, pos, 0.0F, 0.0F);
                b = new Sample(x1, y0, z1, color, false, state, pos, 1.0F, 0.0F);
                c = new Sample(x1, y0, z0, color, false, state, pos, 1.0F, 1.0F);
                d = new Sample(x0, y0, z0, color, false, state, pos, 0.0F, 1.0F);
            }
            case NORTH -> {
                a = new Sample(x1, y1, z0, color, false, state, pos, 0.0F, 0.0F);
                b = new Sample(x0, y1, z0, color, false, state, pos, 1.0F, 0.0F);
                c = new Sample(x0, y0, z0, color, false, state, pos, 1.0F, 1.0F);
                d = new Sample(x1, y0, z0, color, false, state, pos, 0.0F, 1.0F);
            }
            case SOUTH -> {
                a = new Sample(x0, y1, z1, color, false, state, pos, 0.0F, 0.0F);
                b = new Sample(x1, y1, z1, color, false, state, pos, 1.0F, 0.0F);
                c = new Sample(x1, y0, z1, color, false, state, pos, 1.0F, 1.0F);
                d = new Sample(x0, y0, z1, color, false, state, pos, 0.0F, 1.0F);
            }
            case WEST -> {
                a = new Sample(x0, y1, z0, color, false, state, pos, 0.0F, 0.0F);
                b = new Sample(x0, y1, z1, color, false, state, pos, 1.0F, 0.0F);
                c = new Sample(x0, y0, z1, color, false, state, pos, 1.0F, 1.0F);
                d = new Sample(x0, y0, z0, color, false, state, pos, 0.0F, 1.0F);
            }
            case EAST -> {
                a = new Sample(x1, y1, z1, color, false, state, pos, 0.0F, 0.0F);
                b = new Sample(x1, y1, z0, color, false, state, pos, 1.0F, 0.0F);
                c = new Sample(x1, y0, z0, color, false, state, pos, 1.0F, 1.0F);
                d = new Sample(x1, y0, z1, color, false, state, pos, 0.0F, 1.0F);
            }
            default -> {
                return;
            }
        }
        applyTileUv(sprite, a, b, c, d, pos.getX(), pos.getY(), 1, 1);
        addWorldFace(faces, maxFaces, cameraPos, yaw, pitch, radius, fogColor,
                a, b, c, d, color, shadeFor(direction));
    }

    private static QuadVertex quadVertex(int[] data, int vertex, BlockPos pos) {
        int base = vertex * 8;
        return new QuadVertex(
                pos.getX() + Float.intBitsToFloat(data[base]),
                pos.getY() + Float.intBitsToFloat(data[base + 1]),
                pos.getZ() + Float.intBitsToFloat(data[base + 2]),
                Float.intBitsToFloat(data[base + 4]),
                Float.intBitsToFloat(data[base + 5])
        );
    }

    private static void addDetailedSide(List<Face> faces, int maxFaces, WorldChunk chunk,
                                        Vec3d cameraPos, float yaw, float pitch, int radius, int fogColor,
                                        int worldX, int worldZ, int localX, int localZ,
                                        int offsetX, int offsetZ, float ax, float az, float bx, float bz,
                                        float top, int color, float shade, Direction direction) {
        int neighborLocalX = localX + offsetX;
        int neighborLocalZ = localZ + offsetZ;
        float neighborTop = sampleNeighborTop(chunk, worldX + offsetX, worldZ + offsetZ,
                neighborLocalX, neighborLocalZ);
        if (Float.isNaN(neighborTop)) {
            return;
        }
        float bottom = Math.max(
                top - Math.max(1, PortalViewConfig.INDEPENDENT_PORTAL_CHUNK_MESH_SIDE_DEPTH_BLOCKS),
                neighborTop
        );
        if (bottom >= top - 0.08F) {
            return;
        }
        float segmentTop = top;
        int maxDepth = Math.max(1, PortalViewConfig.INDEPENDENT_PORTAL_CHUNK_MESH_SIDE_DEPTH_BLOCKS);
        for (int layer = 0; layer < maxDepth && segmentTop > bottom + 0.08F && faces.size() < maxFaces; layer++) {
            float segmentBottom = Math.max(bottom, segmentTop - 1.0F);
            BlockSample sideSample = blockSampleAtY(chunk, worldX, worldZ, localX, localZ,
                    MathHelper.floor(segmentBottom), direction, color);
            Sprite sprite = sideSample.sprite;
            if (sprite == null) {
                continue;
            }
            int shaded = shadeColor(0xFFFFFFFF, shade);
            Sample a = new Sample(ax, segmentTop, az, shaded, false, sideSample.state, sideSample.pos, 0.0F, 0.0F);
            Sample b = new Sample(bx, segmentTop, bz, shaded, false, sideSample.state, sideSample.pos, 1.0F, 0.0F);
            Sample c = new Sample(bx, segmentBottom, bz, shaded, false, sideSample.state, sideSample.pos, 1.0F, 1.0F);
            Sample d = new Sample(ax, segmentBottom, az, shaded, false, sideSample.state, sideSample.pos, 0.0F, 1.0F);
            applyTileUv(sprite, a, b, c, d, worldX, MathHelper.floor(segmentBottom), 1, 1);
            addWorldFace(faces, maxFaces, cameraPos, yaw, pitch, radius, fogColor,
                    a,
                    b,
                    c,
                    d,
                    shaded,
                    1.0F
            );
            segmentTop = segmentBottom;
        }
    }

    private static void addTexturedTop(List<Face> faces, int maxFaces, WorldChunk chunk, BlockState state,
                                       BlockPos pos, Vec3d cameraPos, float yaw, float pitch,
                                       int radius, int fogColor, float left, float right,
                                       float near, float far, float top, int fallbackColor) {
        int tiles = MathHelper.clamp(PortalViewConfig.INDEPENDENT_PORTAL_CHUNK_MESH_TEXTURE_TILES, 1, 4);
        float width = (right - left) / tiles;
        float depth = (far - near) / tiles;
        for (int z = 0; z < tiles && faces.size() < maxFaces; z++) {
            for (int x = 0; x < tiles && faces.size() < maxFaces; x++) {
                float x0 = left + x * width;
                float x1 = x0 + width;
                float z0 = near + z * depth;
                float z1 = z0 + depth;
                float u = (x + 0.5F) / tiles;
                float v = (z + 0.5F) / tiles;
                Sprite sprite = topSprite(chunk, state, pos);
                if (sprite == null) {
                    continue;
                }
                int color = 0xFFFFFFFF;
                Sample a = new Sample(x0, top, z0, color, false, state, pos, 0.0F, 0.0F);
                Sample b = new Sample(x1, top, z0, color, false, state, pos, 1.0F, 0.0F);
                Sample c = new Sample(x1, top, z1, color, false, state, pos, 1.0F, 1.0F);
                Sample d = new Sample(x0, top, z1, color, false, state, pos, 0.0F, 1.0F);
                applyTileUv(sprite, a, b, c, d, worldXFromFloat(left), worldZFromFloat(near), tiles, tiles);
                addWorldFace(faces, maxFaces, cameraPos, yaw, pitch, radius, fogColor,
                        a,
                        b,
                        c,
                        d,
                        color,
                        1.0F
                );
            }
        }
    }

    private static void addHeightFace(List<Face> faces, int maxFaces, Vec3d cameraPos,
                                      float yaw, float pitch, int radius, int fogColor,
                                      Sample left, Sample right, int color, float shade) {
        float bottom = Math.min(left.y, right.y) - Math.max(2.0F, Math.abs(left.y - right.y));
        if (bottom >= left.y - 0.5F && bottom >= right.y - 0.5F) {
            return;
        }
        Sample a = left.withY(left.y);
        Sample b = right.withY(right.y);
        Sample c = right.withY(bottom);
        Sample d = left.withY(bottom);
        applyFallbackUv(a, b, c, d);
        addWorldFace(faces, maxFaces, cameraPos, yaw, pitch, radius, fogColor, a, b, c, d,
                shadeColor(color, shade), 1.0F);
    }

    private static void addWorldFace(List<Face> faces, int maxFaces, Vec3d cameraPos,
                                     float yaw, float pitch, int radius, int fogColor,
                                     Sample a, Sample b, Sample c, Sample d, int color, float shade) {
        if (faces.size() >= maxFaces) {
            return;
        }
        ProjectedVertex pa = project(a.x, a.y, a.z, cameraPos, yaw, pitch, radius);
        ProjectedVertex pb = project(b.x, b.y, b.z, cameraPos, yaw, pitch, radius);
        ProjectedVertex pc = project(c.x, c.y, c.z, cameraPos, yaw, pitch, radius);
        ProjectedVertex pd = project(d.x, d.y, d.z, cameraPos, yaw, pitch, radius);
        if (pa == null || pb == null || pc == null || pd == null || !isQuadVisible(pa, pb, pc, pd)) {
            return;
        }
        if (screenArea(pa, pb, pc, pd) > 0.55F) {
            return;
        }
        float depth = (pa.depth + pb.depth + pc.depth + pd.depth) * 0.25F;
        float fog = MathHelper.clamp(depth / radius, 0.0F, 1.0F);
        int shaded = shadeColor(color, shade);
        int faded = mixColor(shaded, fogColor, fog * 0.68F);
        faces.add(new Face(
                new TexturedVertex(pa, a.u, a.v),
                new TexturedVertex(pb, b.u, b.v),
                new TexturedVertex(pc, c.u, c.v),
                new TexturedVertex(pd, d.u, d.v),
                faded,
                depth
        ));
    }

    private static Sample sampleTop(WorldChunk chunk, int worldX, int worldZ, int localX, int localZ, int fallbackColor) {
        int y = chunk.sampleHeightmap(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, localX, localZ) - 1;
        BlockPos pos = new BlockPos(worldX, y, worldZ);
        BlockState state = chunk.getBlockState(pos);
        while (y > chunk.getBottomY() && shouldSkipState(state)) {
            y--;
            pos = new BlockPos(worldX, y, worldZ);
            state = chunk.getBlockState(pos);
        }
        int color = fallbackColor;
        if (!shouldSkipState(state)) {
            color = blockFaceColor(chunk, state, pos, Direction.UP, mapColor(state, chunk, pos), 0.5F, 0.5F);
        }
        return new Sample(worldX + 0.5F, y + 1.0F, worldZ + 0.5F, color,
                shouldSkipState(state), state, pos, 0.5F, 0.5F);
    }

    private static float sampleNeighborTop(WorldChunk chunk, int worldX, int worldZ, int localX, int localZ) {
        WorldChunk sampleChunk = chunk;
        int sampleLocalX = localX;
        int sampleLocalZ = localZ;
        if (localX < 0 || localX > 15 || localZ < 0 || localZ > 15) {
            sampleChunk = PortalRemoteChunkCache.getLoadedChunk(ChunkSectionPos.getSectionCoord(worldX), ChunkSectionPos.getSectionCoord(worldZ));
            if (sampleChunk == null) {
                return Float.NaN;
            }
            sampleLocalX = Math.floorMod(worldX, 16);
            sampleLocalZ = Math.floorMod(worldZ, 16);
        }
        int y = sampleChunk.sampleHeightmap(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, sampleLocalX, sampleLocalZ) - 1;
        BlockPos pos = new BlockPos(worldX, y, worldZ);
        BlockState state = sampleChunk.getBlockState(pos);
        while (y > sampleChunk.getBottomY() && shouldSkipState(state)) {
            y--;
            pos = new BlockPos(worldX, y, worldZ);
            state = sampleChunk.getBlockState(pos);
        }
        return shouldSkipState(state) ? Float.NaN : y + 1.0F;
    }

    private static int colorAtY(WorldChunk chunk, int worldX, int worldZ, int localX, int localZ,
                                int y, int fallbackColor, Direction direction) {
        if (localX < 0 || localX > 15 || localZ < 0 || localZ > 15) {
            return fallbackColor;
        }
        BlockPos pos = new BlockPos(worldX, MathHelper.clamp(y, chunk.getBottomY(), chunk.getTopYInclusive()), worldZ);
        BlockState state = chunk.getBlockState(pos);
        if (shouldSkipState(state)) {
            return fallbackColor;
        }
        return blockFaceColor(chunk, state, pos, direction, mapColor(state, chunk, pos), 0.5F, 0.5F);
    }

    private static int blockFaceColor(WorldChunk chunk, BlockState state, BlockPos pos, Direction direction,
                                      int fallbackColor, float u, float v) {
        if (shouldSkipState(state)) {
            return fallbackColor;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return fallbackColor;
        }
        try {
            Random random = Random.create(state.getRenderingSeed(pos));
            for (BlockModelPart part : client.getBlockRenderManager().getModel(state).getParts(random)) {
                List<BakedQuad> quads = part.getQuads(direction);
                if (!quads.isEmpty()) {
                    BakedQuad quad = quads.get(Math.floorMod(pos.hashCode(), quads.size()));
                    return sampleSpriteColor(client, chunk, state, pos, quad.sprite(), quad.tintIndex(),
                            fallbackColor, u, v);
                }
            }
        } catch (RuntimeException ignored) {
            return fallbackColor;
        }
        return blockTextureColor(chunk, state, pos, fallbackColor, u, v);
    }

    private static Sprite topSprite(WorldChunk chunk, BlockState state, BlockPos pos) {
        return faceSprite(chunk, state, pos, Direction.UP);
    }

    private static Sprite faceSprite(WorldChunk chunk, BlockState state, BlockPos pos, Direction direction) {
        if (shouldSkipState(state)) {
            return null;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return null;
        }
        try {
            Random random = Random.create(state.getRenderingSeed(pos));
            for (BlockModelPart part : client.getBlockRenderManager().getModel(state).getParts(random)) {
                List<BakedQuad> quads = part.getQuads(direction);
                if (!quads.isEmpty()) {
                    return quads.get(Math.floorMod(pos.hashCode(), quads.size())).sprite();
                }
            }
            return client.getBlockRenderManager().getModel(state).particleSprite();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static BlockSample blockSampleAtY(WorldChunk chunk, int worldX, int worldZ, int localX, int localZ,
                                              int y, Direction direction, int fallbackColor) {
        if (localX < 0 || localX > 15 || localZ < 0 || localZ > 15) {
            return new BlockSample(null, null, null, fallbackColor);
        }
        BlockPos pos = new BlockPos(worldX, MathHelper.clamp(y, chunk.getBottomY(), chunk.getTopYInclusive()), worldZ);
        BlockState state = chunk.getBlockState(pos);
        if (shouldSkipState(state)) {
            return new BlockSample(null, null, null, fallbackColor);
        }
        int color = blockFaceColor(chunk, state, pos, direction, mapColor(state, chunk, pos), 0.5F, 0.5F);
        return new BlockSample(state, pos, faceSprite(chunk, state, pos, direction), color);
    }

    private static int blockTextureColor(WorldChunk chunk, BlockState state, BlockPos pos,
                                         int fallbackColor, float u, float v) {
        if (shouldSkipState(state)) {
            return fallbackColor;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return fallbackColor;
        }
        try {
            Sprite sprite = client.getBlockRenderManager().getModel(state).particleSprite();
            if (sprite == null || sprite.getContents() == null) {
                return fallbackColor;
            }
            NativeImage image = ((SpriteContentsAccessor) sprite.getContents()).monvhua$getImage();
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                return fallbackColor;
            }
            float atlasU = sprite.getMinU() + (sprite.getMaxU() - sprite.getMinU()) * MathHelper.clamp(u, 0.0F, 1.0F);
            float atlasV = sprite.getMinV() + (sprite.getMaxV() - sprite.getMinV()) * MathHelper.clamp(v, 0.0F, 1.0F);
            int x = MathHelper.clamp((int) sprite.getFrameFromU(atlasU), 0, image.getWidth() - 1);
            int y = MathHelper.clamp((int) sprite.getFrameFromV(atlasV), 0, image.getHeight() - 1);
            int color = image.getColorArgb(x, y);
            if (((color >>> 24) & 0xFF) == 0) {
                return fallbackColor;
            }
            return 0xFF000000 | (color & 0x00FFFFFF);
        } catch (RuntimeException ignored) {
            return fallbackColor;
        }
    }

    private static int sampleSpriteColor(MinecraftClient client, WorldChunk chunk, BlockState state, BlockPos pos,
                                         Sprite sprite, int tintIndex, int fallbackColor, float u, float v) {
        if (sprite == null || sprite.getContents() == null) {
            return fallbackColor;
        }
        NativeImage image = ((SpriteContentsAccessor) sprite.getContents()).monvhua$getImage();
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
            return fallbackColor;
        }
        float atlasU = sprite.getMinU() + (sprite.getMaxU() - sprite.getMinU()) * MathHelper.clamp(u, 0.0F, 1.0F);
        float atlasV = sprite.getMinV() + (sprite.getMaxV() - sprite.getMinV()) * MathHelper.clamp(v, 0.0F, 1.0F);
        int x = MathHelper.clamp((int) sprite.getFrameFromU(atlasU), 0, image.getWidth() - 1);
        int y = MathHelper.clamp((int) sprite.getFrameFromV(atlasV), 0, image.getHeight() - 1);
        int color = image.getColorArgb(x, y);
        if (((color >>> 24) & 0xFF) == 0) {
            return fallbackColor;
        }
        if (tintIndex >= 0) {
            color = multiplyRgb(color, client.getBlockColors().getColor(state, chunk.getWorld(), pos, tintIndex));
        }
        return 0xFF000000 | (color & 0x00FFFFFF);
    }

    private static GpuTextureView blockAtlasTexture() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return null;
        }
        AbstractTexture texture = client.getTextureManager().getTexture(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
        return texture == null ? null : texture.getGlTextureView();
    }

    private static void applyTileUv(Sprite sprite, Sample a, Sample b, Sample c, Sample d,
                                    int seedX, int seedZ, int tilesX, int tilesZ) {
        if (sprite == null) {
            applyFallbackUv(a, b, c, d);
            return;
        }
        float minU = sprite.getMinU();
        float maxU = sprite.getMaxU();
        float minV = sprite.getMinV();
        float maxV = sprite.getMaxV();
        float u0 = MathHelper.lerp(Math.floorMod(seedX, Math.max(1, tilesX)) / (float) Math.max(1, tilesX), minU, maxU);
        float u1 = MathHelper.lerp((Math.floorMod(seedX, Math.max(1, tilesX)) + 1) / (float) Math.max(1, tilesX), minU, maxU);
        float v0 = MathHelper.lerp(Math.floorMod(seedZ, Math.max(1, tilesZ)) / (float) Math.max(1, tilesZ), minV, maxV);
        float v1 = MathHelper.lerp((Math.floorMod(seedZ, Math.max(1, tilesZ)) + 1) / (float) Math.max(1, tilesZ), minV, maxV);
        a.setUv(u0, v0);
        b.setUv(u1, v0);
        c.setUv(u1, v1);
        d.setUv(u0, v1);
    }

    private static void applyFallbackUv(Sample a, Sample b, Sample c, Sample d) {
        a.setUv(0.0F, 0.0F);
        b.setUv(1.0F, 0.0F);
        c.setUv(1.0F, 1.0F);
        d.setUv(0.0F, 1.0F);
    }

    private static int worldXFromFloat(float value) {
        return MathHelper.floor(value);
    }

    private static int worldZFromFloat(float value) {
        return MathHelper.floor(value);
    }

    private static int mapColor(BlockState state, WorldChunk chunk, BlockPos pos) {
        MapColor mapColor = state.getMapColor(chunk.getWorld(), pos);
        return 0xFF000000 | (mapColor.getRenderColor(MapColor.Brightness.NORMAL) & 0x00FFFFFF);
    }

    private static boolean shouldSkipState(BlockState state) {
        return state == null
                || state.isAir()
                || state.isOf(PortalItems.PORTAL_BLOCK)
                || !state.getFluidState().isEmpty()
                || state.getRenderType() == BlockRenderType.INVISIBLE;
    }

    private static boolean isFaceVisible(WorldChunk chunk, BlockPos pos, Direction direction) {
        BlockState neighbor = blockStateAt(chunk, pos.offset(direction));
        if (shouldSkipState(neighbor)) {
            return true;
        }
        return neighbor.getRenderType() != BlockRenderType.MODEL;
    }

    private static BlockState blockStateAt(WorldChunk chunk, BlockPos pos) {
        int chunkX = ChunkSectionPos.getSectionCoord(pos.getX());
        int chunkZ = ChunkSectionPos.getSectionCoord(pos.getZ());
        WorldChunk sampleChunk = chunk.getPos().equals(new ChunkPos(chunkX, chunkZ))
                ? chunk
                : PortalRemoteChunkCache.getLoadedChunk(chunkX, chunkZ);
        return sampleChunk == null ? null : sampleChunk.getBlockState(pos);
    }

    private static float shadeFor(Direction direction) {
        return switch (direction) {
            case UP -> 1.0F;
            case DOWN -> 0.52F;
            case NORTH -> 0.86F;
            case SOUTH -> 0.64F;
            case WEST -> 0.78F;
            case EAST -> 0.70F;
        };
    }

    private static ProjectedVertex project(float x, float y, float z, Vec3d cameraPos,
                                           float yaw, float pitch, int radius) {
        if (activeAperture != null) {
            ProjectedVertex apertureProjected = projectThroughAperture(x, y, z, cameraPos, radius);
            if (apertureProjected != null) {
                return apertureProjected;
            }
            return null;
        }
        double dx = x - cameraPos.x;
        double dy = y - cameraPos.y;
        double dz = z - cameraPos.z;
        double yawRad = Math.toRadians(-yaw);
        double sinYaw = Math.sin(yawRad);
        double cosYaw = Math.cos(yawRad);
        double right = dx * cosYaw - dz * sinYaw;
        double forward = dx * sinYaw + dz * cosYaw;
        if (forward <= 2.0D) {
            return null;
        }
        double pitchRad = Math.toRadians(pitch);
        double sinPitch = Math.sin(pitchRad);
        double cosPitch = Math.cos(pitchRad);
        double up = dy * cosPitch - forward * sinPitch;
        double depth = dy * sinPitch + forward * cosPitch;
        if (depth <= 2.0D) {
            return null;
        }
        float fovScale = 0.74F;
        float screenX = (float) (0.5D - right / depth * fovScale / activeAspect);
        float screenY = (float) (0.5D + up / depth * fovScale);
        if (screenX < -0.35F || screenX > 1.35F || screenY < -0.35F || screenY > 1.35F) {
            return null;
        }
        return new ProjectedVertex(screenX, screenY, (float) Math.min(depth, radius));
    }

    private static ProjectedVertex projectThroughAperture(float x, float y, float z,
                                                          Vec3d cameraPos, int radius) {
        ApertureProjection aperture = activeAperture;
        Vec3d point = new Vec3d(x, y, z);
        Vec3d ray = point.subtract(cameraPos);
        double denominator = ray.dotProduct(aperture.normal);
        if (Math.abs(denominator) < 1.0E-5D) {
            return null;
        }
        double t = aperture.center.subtract(cameraPos).dotProduct(aperture.normal) / denominator;
        if (t <= 0.0D || t >= 1.0D) {
            return null;
        }
        Vec3d hit = cameraPos.add(ray.multiply(t));
        Vec3d local = hit.subtract(aperture.center);
        float screenX = (float) (0.5D + local.dotProduct(aperture.horizontal) / aperture.width);
        float screenY = (float) (0.5D + local.dotProduct(aperture.vertical) / aperture.height);
        if (screenX < -0.35F || screenX > 1.35F || screenY < -0.35F || screenY > 1.35F) {
            return null;
        }
        double depth = ray.length();
        if (depth <= 0.5D) {
            return null;
        }
        return new ProjectedVertex(screenX, screenY, (float) Math.min(depth, radius));
    }

    private static boolean isQuadVisible(ProjectedVertex a, ProjectedVertex b,
                                         ProjectedVertex c, ProjectedVertex d) {
        float minX = Math.min(Math.min(a.x, b.x), Math.min(c.x, d.x));
        float maxX = Math.max(Math.max(a.x, b.x), Math.max(c.x, d.x));
        float minY = Math.min(Math.min(a.y, b.y), Math.min(c.y, d.y));
        float maxY = Math.max(Math.max(a.y, b.y), Math.max(c.y, d.y));
        return maxX >= 0.0F && minX <= 1.0F && maxY >= 0.0F && minY <= 1.0F;
    }

    private static float screenArea(ProjectedVertex a, ProjectedVertex b, ProjectedVertex c, ProjectedVertex d) {
        return Math.abs(
                a.x * b.y - b.x * a.y
                        + b.x * c.y - c.x * b.y
                        + c.x * d.y - d.x * c.y
                        + d.x * a.y - a.x * d.y
        ) * 0.5F;
    }

    private static void renderTerrainBand(SimpleFramebuffer targetFramebuffer, PortalHorizonCache.HorizonData data,
                                          int fogColor, int groundColor, float horizonY) {
        if (!PortalViewConfig.INDEPENDENT_PORTAL_TERRAIN_BAND || data == null || data.sampleCount() <= 0) {
            return;
        }
        GpuBuffer buffer = terrainBuffer(data, fogColor, groundColor, horizonY);
        if (buffer == null || terrainVertexCount <= 0) {
            return;
        }
        try (RenderPass pass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(
                        () -> "Monvhua independent portal terrain",
                        targetFramebuffer.getColorAttachmentView(),
                        OptionalInt.empty(),
                        targetFramebuffer.getDepthAttachmentView(),
                        OptionalDouble.empty()
                )) {
            pass.setPipeline(PortalRenderPipelines.horizon());
            pass.setVertexBuffer(0, buffer);
            pass.draw(0, terrainVertexCount);
        }
    }

    private static GpuBuffer terrainBuffer(PortalHorizonCache.HorizonData data, int fogColor,
                                           int groundColor, float horizonY) {
        int columns = Math.max(4, PortalViewConfig.INDEPENDENT_PORTAL_TERRAIN_COLUMNS);
        int key = Long.hashCode(data.generation());
        key = 31 * key + fogColor;
        key = 31 * key + groundColor;
        key = 31 * key + Math.round(horizonY * 4096.0F);
        key = 31 * key + columns;
        if (terrainBuffer != null && terrainKey == key) {
            return terrainBuffer;
        }
        if (terrainBuffer != null) {
            terrainBuffer.close();
            terrainBuffer = null;
        }

        int side = data.side();
        if (side <= 0) {
            terrainVertexCount = 0;
            return null;
        }

        int drawColumns = Math.min(columns, side);
        ByteBuffer vertices = ByteBuffer.allocateDirect(drawColumns * 6 * 16).order(ByteOrder.nativeOrder());
        int centerZ = MathHelper.clamp(side / 2, 0, side - 1);
        float minTop = MathHelper.clamp(horizonY - 0.10F, 0.06F, 0.92F);
        float maxTop = MathHelper.clamp(horizonY + 0.12F, minTop + 0.01F, 0.96F);
        int nearGround = mixColor(groundColor, fogColor, 0.35F);
        int farGround = mixColor(groundColor, fogColor, 0.70F);

        terrainVertexCount = 0;
        for (int column = 0; column < drawColumns; column++) {
            int sampleX = MathHelper.clamp(Math.round(column * (side - 1) / (float) Math.max(1, drawColumns - 1)), 0, side - 1);
            float left = column / (float) drawColumns;
            float right = (column + 1) / (float) drawColumns;
            float height = normalizedHeight(data, sampleX, centerZ);
            float top = MathHelper.clamp(horizonY + (height - 0.5F) * 0.16F, minTop, maxTop);
            int columnColor = mixColor(data.colorAt(sampleX, centerZ), nearGround, 0.45F);
            putQuad(vertices, left, 0.0F, right, top, columnColor, farGround);
            terrainVertexCount += 6;
        }
        vertices.flip();
        terrainKey = key;
        terrainBuffer = RenderSystem.getDevice()
                .createBuffer(() -> "Monvhua independent portal terrain", 40, vertices);
        return terrainBuffer;
    }

    private static float normalizedHeight(PortalHorizonCache.HorizonData data, int x, int z) {
        int span = Math.max(1, data.maxY() - data.minY());
        return MathHelper.clamp((data.heightAt(x, z) - data.minY()) / (float) span, 0.0F, 1.0F);
    }

    private static float horizonY(PortalHorizonCache.HorizonData data, double cameraY, float pitch) {
        float cameraHeight = data == null ? 0.55F : data.normalizedCameraHeight(cameraY);
        float pitchOffset = MathHelper.clamp(pitch / 90.0F, -1.0F, 1.0F) * 0.38F;
        float heightOffset = MathHelper.clamp((cameraHeight - 0.55F) * 0.22F, -0.16F, 0.16F);
        return MathHelper.clamp(0.50F + pitchOffset + heightOffset, 0.16F, 0.84F);
    }

    private static int softenGroundColor(int groundColor, int fogColor) {
        int r = mix((groundColor >> 16) & 0xFF, (fogColor >> 16) & 0xFF, 0.45F);
        int g = mix((groundColor >> 8) & 0xFF, (fogColor >> 8) & 0xFF, 0.45F);
        int b = mix(groundColor & 0xFF, fogColor & 0xFF, 0.45F);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int mixColor(int fromColor, int toColor, float amount) {
        int r = mix((fromColor >> 16) & 0xFF, (toColor >> 16) & 0xFF, amount);
        int g = mix((fromColor >> 8) & 0xFF, (toColor >> 8) & 0xFF, amount);
        int b = mix(fromColor & 0xFF, toColor & 0xFF, amount);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int mix(int from, int to, float amount) {
        return MathHelper.clamp(Math.round(from + (to - from) * amount), 0, 255);
    }

    private static int multiplyRgb(int color, int tint) {
        int alpha = color & 0xFF000000;
        int red = ((color >>> 16) & 0xFF) * ((tint >>> 16) & 0xFF) / 255;
        int green = ((color >>> 8) & 0xFF) * ((tint >>> 8) & 0xFF) / 255;
        int blue = (color & 0xFF) * (tint & 0xFF) / 255;
        return alpha | (red << 16) | (green << 8) | blue;
    }

    private static int shadeColor(int color, float shade) {
        int r = MathHelper.clamp(Math.round(((color >> 16) & 0xFF) * shade), 0, 255);
        int g = MathHelper.clamp(Math.round(((color >> 8) & 0xFF) * shade), 0, 255);
        int b = MathHelper.clamp(Math.round((color & 0xFF) * shade), 0, 255);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static float textureNoise(float x, float z) {
        long hash = MathHelper.hashCode(MathHelper.floor(x), 0, MathHelper.floor(z));
        return 0.88F + ((hash & 15L) / 15.0F) * 0.20F;
    }

    private static GpuBuffer backgroundBuffer(PortalHorizonCache.HorizonData data,
                                              int skyColor, int fogColor, int groundColor,
                                              float horizonY, float yaw) {
        int quantizedHorizon = Math.round(horizonY * 4096.0F);
        int key = data == null ? 0 : Long.hashCode(data.generation());
        key = 31 * key + skyColor;
        key = 31 * key + fogColor;
        key = 31 * key + groundColor;
        key = 31 * key + quantizedHorizon;
        key = 31 * key + Math.round(yaw * 8.0F);
        if (backgroundBuffer != null && backgroundKey == key) {
            return backgroundBuffer;
        }
        if (backgroundBuffer != null) {
            backgroundBuffer.close();
            backgroundBuffer = null;
        }

        float horizon = quantizedHorizon / 4096.0F;
        float blendTop = MathHelper.clamp(horizon - 0.08F, 0.0F, 1.0F);
        float blendBottom = MathHelper.clamp(horizon + 0.12F, 0.0F, 1.0F);

        if (data == null || data.sampleCount() <= 0 || data.side() <= 1
                || PortalViewConfig.INDEPENDENT_PORTAL_CHUNK_MESH) {
            ByteBuffer vertices = ByteBuffer.allocateDirect(18 * 16).order(ByteOrder.nativeOrder());
            putQuad(vertices, 0.0F, blendTop, 1.0F, 1.0F, skyColor, skyColor);
            putQuad(vertices, 0.0F, blendBottom, 1.0F, blendTop, fogColor, skyColor);
            putQuad(vertices, 0.0F, 0.0F, 1.0F, blendBottom, groundColor, fogColor);
            vertices.flip();
            backgroundVertexCount = 18;
            backgroundKey = key;
            backgroundBuffer = RenderSystem.getDevice()
                    .createBuffer(() -> "Monvhua independent portal background", 40, vertices);
            return backgroundBuffer;
        }

        int side = data.side();
        int columns = MathHelper.clamp(Math.min(side, PortalViewConfig.INDEPENDENT_PORTAL_TERRAIN_COLUMNS), 8, 64);
        int rows = 24;
        ByteBuffer vertices = ByteBuffer.allocateDirect(columns * rows * 6 * 16).order(ByteOrder.nativeOrder());
        for (int row = 0; row < rows; row++) {
            float bottom = row / (float) rows;
            float top = (row + 1) / (float) rows;
            for (int column = 0; column < columns; column++) {
                float left = column / (float) columns;
                float right = (column + 1) / (float) columns;
                float columnT = (column + 0.5F) / columns;
                int bottomColor = mappedBackgroundColor(data, columnT, bottom, horizon, yaw,
                        skyColor, fogColor, groundColor);
                int topColor = mappedBackgroundColor(data, columnT, top, horizon, yaw,
                        skyColor, fogColor, groundColor);
                putQuad(vertices, left, bottom, right, top, bottomColor, topColor);
            }
        }
        vertices.flip();
        backgroundVertexCount = columns * rows * 6;

        backgroundKey = key;
        backgroundBuffer = RenderSystem.getDevice()
                .createBuffer(() -> "Monvhua independent portal background", 40, vertices);
        return backgroundBuffer;
    }

    private static int mappedBackgroundColor(PortalHorizonCache.HorizonData data, float columnT,
                                             float screenY, float horizonY, float yaw,
                                             int skyColor, int fogColor, int groundColor) {
        int side = data.side();
        int center = MathHelper.clamp(side / 2, 0, side - 1);
        float horizontalAngle = (columnT - 0.5F) * 70.0F / activeAspect;
        double radians = Math.toRadians(yaw + horizontalAngle);
        double forwardX = -Math.sin(radians);
        double forwardZ = Math.cos(radians);
        int sampleX;
        int sampleZ;
        if (screenY <= horizonY) {
            float groundT = MathHelper.clamp(screenY / Math.max(0.01F, horizonY), 0.0F, 1.0F);
            float distance = MathHelper.lerp(groundT, 1.0F, Math.max(1.0F, data.gridRadius()));
            sampleX = MathHelper.clamp(center + Math.round((float) (forwardX * distance)), 0, side - 1);
            sampleZ = MathHelper.clamp(center + Math.round((float) (forwardZ * distance)), 0, side - 1);
        } else {
            float distance = Math.max(1.0F, data.gridRadius());
            sampleX = MathHelper.clamp(center + Math.round((float) (forwardX * distance)), 0, side - 1);
            sampleZ = MathHelper.clamp(center + Math.round((float) (forwardZ * distance)), 0, side - 1);
        }

        int mappedColor = data.colorAt(sampleX, sampleZ);
        if (screenY <= horizonY) {
            float groundT = MathHelper.clamp(screenY / Math.max(0.01F, horizonY), 0.0F, 1.0F);
            int grounded = mixColor(mappedColor, groundColor, 0.10F);
            return mixColor(grounded, fogColor, (1.0F - groundT) * 0.18F + groundT * 0.42F);
        }

        float skyT = MathHelper.clamp((screenY - horizonY) / Math.max(0.01F, 1.0F - horizonY), 0.0F, 1.0F);
        int horizonColor = mixColor(mappedColor, fogColor, 0.55F + skyT * 0.20F);
        return mixColor(horizonColor, skyColor, 0.20F + skyT * 0.45F);
    }

    private static int quadVertexColor(MinecraftClient client, WorldChunk chunk, BlockState state,
                                       BlockPos pos, int tintIndex) {
        if (client == null || tintIndex < 0) {
            return 0xFFFFFFFF;
        }
        try {
            return 0xFF000000 | (client.getBlockColors()
                    .getColor(state, chunk.getWorld(), pos, tintIndex) & 0x00FFFFFF);
        } catch (RuntimeException ignored) {
            return 0xFFFFFFFF;
        }
    }

    private static void putQuad(ByteBuffer buffer, float left, float bottom, float right, float top,
                                int bottomColor, int topColor) {
        putVertex(buffer, left, bottom, bottomColor);
        putVertex(buffer, right, bottom, bottomColor);
        putVertex(buffer, right, top, topColor);
        putVertex(buffer, left, bottom, bottomColor);
        putVertex(buffer, right, top, topColor);
        putVertex(buffer, left, top, topColor);
    }

    private static void putTriangle(ByteBuffer buffer, TexturedVertex a, TexturedVertex b,
                                    TexturedVertex c, int color) {
        putTexturedVertex(buffer, a.projected.x, a.projected.y, a.u, a.v, color);
        putTexturedVertex(buffer, b.projected.x, b.projected.y, b.u, b.v, color);
        putTexturedVertex(buffer, c.projected.x, c.projected.y, c.u, c.v, color);
    }

    private static void putFace(ByteBuffer buffer, Face face) {
        putTriangle(buffer, face.a, face.b, face.c, face.color);
        putTriangle(buffer, face.a, face.c, face.d, face.color);
    }

    private static void putVertex(ByteBuffer buffer, float x, float y, int color) {
        buffer.putFloat(x);
        buffer.putFloat(y);
        buffer.putFloat(0.0F);
        buffer.put((byte) ((color >> 16) & 0xFF));
        buffer.put((byte) ((color >> 8) & 0xFF));
        buffer.put((byte) (color & 0xFF));
        buffer.put((byte) ((color >> 24) & 0xFF));
    }

    private static void putTexturedVertex(ByteBuffer buffer, float x, float y, float u, float v, int color) {
        buffer.putFloat(x);
        buffer.putFloat(y);
        buffer.putFloat(0.0F);
        buffer.putFloat(u);
        buffer.putFloat(v);
        buffer.put((byte) ((color >> 16) & 0xFF));
        buffer.put((byte) ((color >> 8) & 0xFF));
        buffer.put((byte) (color & 0xFF));
        buffer.put((byte) ((color >> 24) & 0xFF));
    }

    public static void cleanup() {
        if (backgroundBuffer != null) {
            backgroundBuffer.close();
            backgroundBuffer = null;
        }
        if (terrainBuffer != null) {
            terrainBuffer.close();
            terrainBuffer = null;
        }
        if (chunkMeshBuffer != null) {
            chunkMeshBuffer.close();
            chunkMeshBuffer = null;
        }
    }

    private static final class Sample {
        private final float x;
        private final float y;
        private final float z;
        private final int color;
        private final boolean air;
        private final BlockState state;
        private final BlockPos pos;
        private float u;
        private float v;

        private Sample(float x, float y, float z, int color, boolean air,
                       BlockState state, BlockPos pos, float u, float v) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.color = color;
            this.air = air;
            this.state = state;
            this.pos = pos;
            this.u = u;
            this.v = v;
        }

        Sample withY(float newY) {
            return new Sample(x, newY, z, color, air, state, pos, u, v);
        }

        private void setUv(float u, float v) {
            this.u = u;
            this.v = v;
        }
    }

    private record ProjectedVertex(float x, float y, float depth) {
    }

    private record QuadVertex(float x, float y, float z, float u, float v) {
    }

    private record BlockSample(BlockState state, BlockPos pos, Sprite sprite, int color) {
    }

    private record TexturedVertex(ProjectedVertex projected, float u, float v) {
    }

    private record Face(TexturedVertex a, TexturedVertex b, TexturedVertex c,
                        TexturedVertex d, int color, float depth) {
    }

    private record ApertureProjection(Vec3d center, Vec3d horizontal, Vec3d vertical,
                                      Vec3d normal, double width, double height) {
        private static ApertureProjection from(PortalBlockEntity portal) {
            if (portal == null) {
                return null;
            }
            Direction facing = portal.getFacing();
            double width = Math.max(
                    0.01D,
                    portal.getPortalWidth() - PortalViewConfig.PORTAL_SURFACE_HORIZONTAL_INSET * 2.0D
            );
            double height = Math.max(
                    0.01D,
                    portal.getPortalHeight() - PortalViewConfig.PORTAL_SURFACE_VERTICAL_INSET * 2.0D
            );
            return new ApertureProjection(
                    portal.getPortalCenter(),
                    PortalTransform.surfaceWidthAxis(facing),
                    PortalTransform.surfaceHeightAxis(facing),
                    PortalTransform.normal(facing),
                    width,
                    height
            );
        }
    }
}
