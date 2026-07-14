package com.kuilunfuzhe.monvhua.features.paint;

import com.kuilunfuzhe.monvhua.item.modblock.ModBlocks;
import com.kuilunfuzhe.monvhua.item.paint.PaintItems;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;

public final class PaintToolTargetPreviewRenderer {
    private static final double RAY_DISTANCE = 64.0D;
    private static final double PIXEL_SIZE = 1.0D / PaintOverlayStore.SIZE;
    private static final int VALID_FILL = 0x22FFD34A;
    private static final int INVALID_FILL = 0x22FF3B30;

    private PaintToolTargetPreviewRenderer() {
    }

    public static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.player == null
                || context.matrixStack() == null || context.consumers() == null) {
            return;
        }
        PaintOverlayClient.EditorTool tool = currentTool(client);
        if (!isPreviewTool(tool)) {
            return;
        }
        BlockHitResult hit = currentBlockHit(client);
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        boolean valid = isPaintableBlock(client, pos);
        Vec3d camera = context.camera().getPos();
        Matrix4f matrix = context.matrixStack().peek().getPositionMatrix();
        VertexConsumer vertices = context.consumers().getBuffer(PaintRenderLayers.paintOverlay());
        int color = valid ? VALID_FILL : INVALID_FILL;
        if (tool == PaintOverlayClient.EditorTool.ERASER && PaintOverlayClient.eraserFaceMode()) {
            appendFaceModeDisk(vertices, matrix, camera, pos, hit.getSide(),
                    PaintOverlayClient.selectedRadius(PaintOverlayClient.EditorTool.ERASER), color);
            return;
        }
        VoxelCubeMesh mesh = VoxelCubeMesh.forRadius(previewRadiusPixels(tool));
        Vec3d center = hit.getPos();
        double x = center.x - camera.x;
        double y = center.y - camera.y;
        double z = center.z - camera.z;
        appendVoxelCube(vertices, matrix, x, y, z, mesh, color);
    }

    private static PaintOverlayClient.EditorTool currentTool(MinecraftClient client) {
        if (client.currentScreen instanceof PaintEditorScreen screen) {
            return screen.previewTool();
        }
        if (client.currentScreen != null) {
            return PaintOverlayClient.EditorTool.NONE;
        }
        if (isHolding(client.player.getMainHandStack(), PaintItems.ERASER)
                || isHolding(client.player.getOffHandStack(), PaintItems.ERASER)) {
            return PaintOverlayClient.EditorTool.ERASER;
        }
        if (isHolding(client.player.getMainHandStack(), PaintItems.PAINT_SPRAY_CAN)
                || isHolding(client.player.getOffHandStack(), PaintItems.PAINT_SPRAY_CAN)) {
            return PaintOverlayClient.EditorTool.SPRAY;
        }
        if (isHolding(client.player.getMainHandStack(), PaintItems.PAINT_BRUSH)
                || isHolding(client.player.getOffHandStack(), PaintItems.PAINT_BRUSH)) {
            return PaintOverlayClient.EditorTool.BRUSH;
        }
        return PaintOverlayClient.EditorTool.NONE;
    }

    private static boolean isHolding(ItemStack stack, net.minecraft.item.Item item) {
        return stack != null && stack.getItem() == item;
    }

    private static boolean isPreviewTool(PaintOverlayClient.EditorTool tool) {
        return tool == PaintOverlayClient.EditorTool.BRUSH
                || tool == PaintOverlayClient.EditorTool.ERASER
                || tool == PaintOverlayClient.EditorTool.SPRAY;
    }

    private static BlockHitResult currentBlockHit(MinecraftClient client) {
        if (client.currentScreen instanceof PaintEditorScreen screen) {
            if (!screen.previewMouseTargetsWorld()) {
                return null;
            }
            Ray ray = screenRay(client, screen.previewMouseX(), screen.previewMouseY(),
                    client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
            return raycastBlock(client, ray);
        }
        return client.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }

    private static boolean isPaintableBlock(MinecraftClient client, BlockPos pos) {
        if (client.world == null) {
            return false;
        }
        if (client.world.getBlockState(pos).getBlock() == ModBlocks.DRAWING_BOARD
                || client.world.getBlockState(pos).getBlock() == PaintItems.PAINT_BUCKET_BLOCK) {
            return false;
        }
        return PaintOverlayFeature.canPlacePaint(client.world, pos);
    }

    private static int previewRadiusPixels(PaintOverlayClient.EditorTool tool) {
        return MathHelper.clamp(PaintOverlayClient.selectedRadius(tool),
                PaintOverlayFeature.MIN_RADIUS, PaintOverlayFeature.MAX_MANUAL_RADIUS);
    }

    private static BlockHitResult raycastBlock(MinecraftClient client, Ray ray) {
        if (client.world == null || client.player == null || ray == null) {
            return null;
        }
        HitResult hit = client.world.raycast(new RaycastContext(
                ray.start(),
                ray.end(),
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                client.player
        ));
        return hit instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK ? blockHit : null;
    }

    private static Ray screenRay(MinecraftClient client, double mouseX, double mouseY, int width, int height) {
        if (client == null || client.player == null || width <= 0 || height <= 0 || mouseX < 0.0D || mouseY < 0.0D) {
            return null;
        }
        Vec3d direction = screenDirection(client, mouseX, mouseY, width, height);
        if (direction == null) {
            return null;
        }
        Vec3d start = client.gameRenderer.getCamera().getPos();
        return new Ray(start, start.add(direction.multiply(RAY_DISTANCE)));
    }

    private static Vec3d screenDirection(MinecraftClient client, double mouseX, double mouseY, int width, int height) {
        Camera camera = client.gameRenderer.getCamera();
        double ndcX = MathHelper.clamp((mouseX / width - 0.5D) * 2.0D, -1.0D, 1.0D);
        double ndcY = MathHelper.clamp((0.5D - mouseY / height) * 2.0D, -1.0D, 1.0D);
        Vec3d cameraPos = camera.getPos();
        Vec3d forward = new Vec3d(camera.getHorizontalPlane()).normalize();
        Vec3d right = new Vec3d(camera.getDiagonalPlane()).normalize();
        Vec3d up = new Vec3d(camera.getVerticalPlane()).normalize();
        double sampleDistance = 8.0D;
        Vec3d centerWorld = cameraPos.add(forward.multiply(sampleDistance));
        Vec3d center = client.gameRenderer.project(centerWorld);
        Vec3d rightPoint = client.gameRenderer.project(centerWorld.add(right.multiply(sampleDistance)));
        Vec3d upPoint = client.gameRenderer.project(centerWorld.add(up.multiply(sampleDistance)));
        double rightScale = rightPoint.x - center.x;
        double upScale = upPoint.y - center.y;
        if (!Double.isFinite(rightScale) || !Double.isFinite(upScale)
                || Math.abs(rightScale) <= 1.0E-6D || Math.abs(upScale) <= 1.0E-6D) {
            return camera.getProjection().getPosition((float) ndcX, (float) ndcY).normalize();
        }
        Vec3d target = centerWorld
                .add(right.multiply((ndcX - center.x) / rightScale * sampleDistance))
                .add(up.multiply((ndcY - center.y) / upScale * sampleDistance));
        return target.subtract(cameraPos).normalize();
    }

    private static void appendVoxelCube(VertexConsumer vertices, Matrix4f matrix,
                                        double centerX, double centerY, double centerZ,
                                        VoxelCubeMesh mesh, int color) {
        for (int vertex = 0; vertex < mesh.vertexCount(); vertex++) {
            int index = vertex * 3;
            vertices.vertex(matrix,
                        (float) (centerX + mesh.position(index) * PIXEL_SIZE),
                        (float) (centerY + mesh.position(index + 1) * PIXEL_SIZE),
                        (float) (centerZ + mesh.position(index + 2) * PIXEL_SIZE))
                .color((color >>> 16) & 0xFF, (color >>> 8) & 0xFF, color & 0xFF, (color >>> 24) & 0xFF)
                .texture(0.0F, 0.0F)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(mesh.normal(index), mesh.normal(index + 1), mesh.normal(index + 2));
        }
    }

    private static void appendFaceModeDisk(VertexConsumer vertices, Matrix4f matrix, Vec3d camera,
                                           BlockPos pos, Direction face, int radius, int color) {
        int blockRadius = Math.max(0, MathHelper.clamp(radius,
                PaintOverlayFeature.MIN_RADIUS, PaintOverlayFeature.MAX_MANUAL_RADIUS) - 1);
        double diskRadius = blockRadius + 0.5D;
        Vec3d normal = normal(face);
        Vec3d u = faceU(face);
        Vec3d v = faceV(face);
        Vec3d center = faceCenter(pos, face).add(normal.multiply(0.006D));
        int segments = 64;
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0D * i / segments;
            double a1 = Math.PI * 2.0D * (i + 1) / segments;
            Vec3d p0 = center;
            Vec3d p1 = center.add(u.multiply(Math.cos(a0) * diskRadius)).add(v.multiply(Math.sin(a0) * diskRadius));
            Vec3d p2 = center.add(u.multiply(Math.cos(a1) * diskRadius)).add(v.multiply(Math.sin(a1) * diskRadius));
            appendVertex(vertices, matrix, camera, p0, normal, color);
            appendVertex(vertices, matrix, camera, p1, normal, color);
            appendVertex(vertices, matrix, camera, p2, normal, color);
        }
    }

    private static Vec3d faceCenter(BlockPos pos, Direction face) {
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.5D;
        double z = pos.getZ() + 0.5D;
        return switch (face) {
            case DOWN -> new Vec3d(x, pos.getY(), z);
            case UP -> new Vec3d(x, pos.getY() + 1.0D, z);
            case NORTH -> new Vec3d(x, y, pos.getZ());
            case SOUTH -> new Vec3d(x, y, pos.getZ() + 1.0D);
            case WEST -> new Vec3d(pos.getX(), y, z);
            case EAST -> new Vec3d(pos.getX() + 1.0D, y, z);
        };
    }

    private static Vec3d normal(Direction face) {
        return new Vec3d(face.getOffsetX(), face.getOffsetY(), face.getOffsetZ());
    }

    private static Vec3d faceU(Direction face) {
        return switch (face) {
            case UP, DOWN, SOUTH -> new Vec3d(1.0D, 0.0D, 0.0D);
            case NORTH -> new Vec3d(-1.0D, 0.0D, 0.0D);
            case WEST -> new Vec3d(0.0D, 0.0D, 1.0D);
            case EAST -> new Vec3d(0.0D, 0.0D, -1.0D);
        };
    }

    private static Vec3d faceV(Direction face) {
        return switch (face) {
            case UP -> new Vec3d(0.0D, 0.0D, -1.0D);
            case DOWN -> new Vec3d(0.0D, 0.0D, 1.0D);
            case NORTH, SOUTH, WEST, EAST -> new Vec3d(0.0D, 1.0D, 0.0D);
        };
    }

    private static void appendVertex(VertexConsumer vertices, Matrix4f matrix, Vec3d camera,
                                     Vec3d point, Vec3d normal, int color) {
        vertices.vertex(matrix,
                    (float) (point.x - camera.x),
                    (float) (point.y - camera.y),
                    (float) (point.z - camera.z))
            .color((color >>> 16) & 0xFF, (color >>> 8) & 0xFF, color & 0xFF, (color >>> 24) & 0xFF)
            .texture(0.0F, 0.0F)
            .overlay(OverlayTexture.DEFAULT_UV)
            .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
            .normal((float) normal.x, (float) normal.y, (float) normal.z);
    }

    private record Ray(Vec3d start, Vec3d end) {
    }
}
