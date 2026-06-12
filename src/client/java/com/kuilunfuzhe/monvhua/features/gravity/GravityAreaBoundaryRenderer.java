package com.kuilunfuzhe.monvhua.features.gravity;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class GravityAreaBoundaryRenderer {
    private GravityAreaBoundaryRenderer() {
    }

    public static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        PlayerEntity player = client.player;
        if (client.world == null || player == null || !GravityDebugClient.isHoldingDebugStick(client)) {
            return;
        }

        MatrixStack matrices = context.matrixStack();
        Vec3d camera = context.camera().getPos();
        VertexConsumer lineVertices = context.consumers().getBuffer(RenderLayer.getLines());
        VertexConsumer faceVertices = context.consumers().getBuffer(RenderLayer.getDebugQuads());
        long time = client.world.getTime();
        float pulse = 0.14F + 0.12F * (float) (Math.sin(time * 0.22D) + 1.0D);
        float linePhase = (time % 40L) / 40.0F;
        float existingLineAlpha = flashingAlpha(linePhase);

        List<GravityMagic.AreaGravityView> views = GravityMagic.getClientAreaGravityViews(client.world.getRegistryKey());
        if (!views.isEmpty()) {
            Set<BlockPos> existingBlocks = new HashSet<>();
            for (GravityMagic.AreaGravityView view : views) {
                existingBlocks.addAll(view.spec().coveredBlocks(view.center()));
            }
            renderBlockSet(matrices, lineVertices, faceVertices, camera, existingBlocks,
                    pos -> isCoveredByAnyView(views, existingBlocks, pos),
                    pulse, 0.92F, 0.92F, 0.86F, existingLineAlpha, 1.0F, 0.95F, 0.1F);
        }

        BlockPos preview = GravityDebugClient.previewCenter(client);
        if (preview != null) {
            GravityAreaSpec spec = GravityDebugClient.previewSpec();
            Set<BlockPos> previewBlocks = new HashSet<>(spec.coveredBlocks(preview));
            float r = GravityDebugClient.mode() == GravityDebugClient.ToolMode.SELECTION ? 0.35F : 1.0F;
            float g = GravityDebugClient.mode() == GravityDebugClient.ToolMode.SELECTION ? 0.7F : 0.72F;
            float b = GravityDebugClient.mode() == GravityDebugClient.ToolMode.SELECTION ? 1.0F : 0.18F;
            renderBlockSet(matrices, lineVertices, faceVertices, camera, previewBlocks,
                    pos -> previewBlocks.contains(pos) || spec.containsBlock(preview, pos),
                    0.22F, 0.95F, 0.95F, 0.9F, 1.0F, r, g, b);
        }
    }

    private static void renderBlockSet(MatrixStack matrices, VertexConsumer lineVertices, VertexConsumer faceVertices, Vec3d camera,
                                       Set<BlockPos> blocks, Coverage coverage,
                                       float faceAlpha, float faceRed, float faceGreen, float faceBlue,
                                       float lineAlpha, float lineRed, float lineGreen, float lineBlue) {
        for (BlockPos pos : blocks) {
            if (!isBoundary(pos, coverage)) {
                continue;
            }
            float minX = (float) (pos.getX() - camera.x);
            float minY = (float) (pos.getY() - camera.y);
            float minZ = (float) (pos.getZ() - camera.z);
            float maxX = minX + 1.0F;
            float maxY = minY + 1.0F;
            float maxZ = minZ + 1.0F;
            for (Direction direction : Direction.values()) {
                if (coverage.contains(pos.offset(direction))) {
                    continue;
                }
                VertexRendering.drawSide(matrices, faceVertices, direction, minX, minY, minZ, maxX, maxY, maxZ, faceRed, faceGreen, faceBlue, faceAlpha);
                if (lineAlpha > 0.0F) {
                    drawFaceOuterEdges(matrices, lineVertices, pos, direction, coverage,
                            minX, minY, minZ, maxX, maxY, maxZ, lineRed, lineGreen, lineBlue, lineAlpha);
                }
            }
        }
    }

    private static boolean isBoundary(BlockPos pos, Coverage coverage) {
        for (Direction direction : Direction.values()) {
            if (!coverage.contains(pos.offset(direction))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCoveredByAnyView(List<GravityMagic.AreaGravityView> views, Set<BlockPos> blocks, BlockPos pos) {
        if (blocks.contains(pos)) {
            return true;
        }
        for (GravityMagic.AreaGravityView view : views) {
            if (view.spec().containsBlock(view.center(), pos)) {
                return true;
            }
        }
        return false;
    }

    private static void drawFaceOuterEdges(MatrixStack matrices, VertexConsumer vertices,
                                           BlockPos pos, Direction face, Coverage coverage,
                                           float minX, float minY, float minZ, float maxX, float maxY, float maxZ,
                                           float red, float green, float blue, float alpha) {
        for (Direction side : Direction.values()) {
            if (side.getAxis() == face.getAxis()) {
                continue;
            }
            BlockPos sideNeighbor = pos.offset(side);
            if (coverage.contains(sideNeighbor)
                    && !coverage.contains(sideNeighbor.offset(face))) {
                continue;
            }
            drawEdge(matrices, vertices, face, side, minX, minY, minZ, maxX, maxY, maxZ, red, green, blue, alpha);
        }
    }

    private static void drawEdge(MatrixStack matrices, VertexConsumer vertices, Direction face, Direction side,
                                 float minX, float minY, float minZ, float maxX, float maxY, float maxZ,
                                 float red, float green, float blue, float alpha) {
        float[] a = edgePoint(face, side, minX, minY, minZ, maxX, maxY, maxZ, false);
        float[] b = edgePoint(face, side, minX, minY, minZ, maxX, maxY, maxZ, true);
        float normalX = b[0] - a[0];
        float normalY = b[1] - a[1];
        float normalZ = b[2] - a[2];
        float length = (float) Math.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
        if (length > 0.0F) {
            normalX /= length;
            normalY /= length;
            normalZ /= length;
        }
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        vertices.vertex(matrix, a[0], a[1], a[2]).color(red, green, blue, alpha).normal(normalX, normalY, normalZ);
        vertices.vertex(matrix, b[0], b[1], b[2]).color(red, green, blue, alpha).normal(normalX, normalY, normalZ);
    }

    private static float[] edgePoint(Direction face, Direction side,
                                     float minX, float minY, float minZ, float maxX, float maxY, float maxZ,
                                     boolean maxAlongEdge) {
        Direction.Axis edgeAxis = remainingAxis(face.getAxis(), side.getAxis());
        float x = fixedOrEdge(Direction.Axis.X, face, side, edgeAxis, minX, maxX, maxAlongEdge);
        float y = fixedOrEdge(Direction.Axis.Y, face, side, edgeAxis, minY, maxY, maxAlongEdge);
        float z = fixedOrEdge(Direction.Axis.Z, face, side, edgeAxis, minZ, maxZ, maxAlongEdge);
        return new float[] {x, y, z};
    }

    private static float fixedOrEdge(Direction.Axis axis, Direction face, Direction side, Direction.Axis edgeAxis,
                                     float min, float max, boolean maxAlongEdge) {
        if (axis == edgeAxis) {
            return maxAlongEdge ? max : min;
        }
        if (axis == face.getAxis()) {
            return face.getDirection() == Direction.AxisDirection.POSITIVE ? max : min;
        }
        return side.getDirection() == Direction.AxisDirection.POSITIVE ? max : min;
    }

    private static Direction.Axis remainingAxis(Direction.Axis first, Direction.Axis second) {
        for (Direction.Axis axis : Direction.Axis.values()) {
            if (axis != first && axis != second) {
                return axis;
            }
        }
        return Direction.Axis.Y;
    }

    private static float flashingAlpha(float phase) {
        if (phase < 0.45F) {
            return 1.0F - smoothStep(phase / 0.45F);
        }
        if (phase < 0.70F) {
            return 0.0F;
        }
        return smoothStep((phase - 0.70F) / 0.30F);
    }

    private static float smoothStep(float value) {
        float clamped = Math.clamp(value, 0.0F, 1.0F);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    @FunctionalInterface
    private interface Coverage {
        boolean contains(BlockPos pos);
    }
}
