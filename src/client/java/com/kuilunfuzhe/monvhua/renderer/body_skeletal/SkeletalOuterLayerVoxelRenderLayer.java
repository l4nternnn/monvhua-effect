package com.kuilunfuzhe.monvhua.renderer.body_skeletal;

import com.kuilunfuzhe.monvhua.features.block.body_skeletal.SkeletalBodyPart;
import com.kuilunfuzhe.monvhua.features.block.body_skeletal.SkeletalBodyPartBlockEntity;
import com.kuilunfuzhe.monvhua.features.paint.SkinTexturePixels;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.Optional;
import java.util.function.BiConsumer;

public class SkeletalOuterLayerVoxelRenderLayer<T extends SkeletalBodyPartBlockEntity>
        extends GeoRenderLayer<T, Void, GeoRenderState> {
    private static final Identifier WHITE_TEXTURE = Identifier.ofVanilla("textures/block/white_concrete.png");
    private static final RenderLayer VOXEL_LAYER = RenderLayer.getEntityCutout(WHITE_TEXTURE);
    private static final float OUTER_THICKNESS = 0.5F;

    public SkeletalOuterLayerVoxelRenderLayer(GeoRenderer<T, Void, GeoRenderState> renderer) {
        super(renderer);
    }

    @Override
    public void addPerBoneRender(GeoRenderState renderState, BakedGeoModel model,
                                 BiConsumer<GeoBone, software.bernie.geckolib.renderer.base.PerBoneRender<GeoRenderState>> consumer) {
        SkeletalBodyPartBlockEntity entity = renderState.getOrDefaultGeckolibData(SkeletalBodyPartGeoModel.BLOCK_ENTITY, null);
        if (entity == null) {
            return;
        }

        boolean slim = "slim".equals(entity.getSkinType());
        switch (entity.getPart()) {
            case HEAD -> add(model, consumer, "head", PartRenderer.head());
            case TORSO -> {
                add(model, consumer, "torso", PartRenderer.torsoUpper());
                add(model, consumer, "waist", PartRenderer.torsoLower());
            }
            case LEFT_ARM -> {
                add(model, consumer, "left_arm", PartRenderer.leftArmUpper(slim));
                add(model, consumer, "left_forearm", PartRenderer.leftArmLower(slim));
            }
            case RIGHT_ARM -> {
                add(model, consumer, "right_arm", PartRenderer.rightArmUpper(slim));
                add(model, consumer, "right_forearm", PartRenderer.rightArmLower(slim));
            }
            case LEFT_LEG -> {
                add(model, consumer, "left_leg", PartRenderer.leftLegUpper());
                add(model, consumer, "left_lower_leg", PartRenderer.leftLegLower());
            }
            case RIGHT_LEG -> {
                add(model, consumer, "right_leg", PartRenderer.rightLegUpper());
                add(model, consumer, "right_lower_leg", PartRenderer.rightLegLower());
            }
        }
    }

    private void add(BakedGeoModel model,
                     BiConsumer<GeoBone, software.bernie.geckolib.renderer.base.PerBoneRender<GeoRenderState>> consumer,
                     String boneName, PartRenderer renderer) {
        Optional<GeoBone> bone = model.getBone(boneName);
        bone.ifPresent(value -> consumer.accept(value, (renderState, matrices, ignoredBone, ignoredLayer,
                                                        vertexConsumers, light, overlay, ignoredColor) ->
                renderPart(renderState, matrices, vertexConsumers, light, overlay, renderer)));
    }

    private void renderPart(GeoRenderState renderState, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                            int light, int overlay, PartRenderer renderer) {
        Identifier texture = getTextureResource(renderState);
        SkinTexturePixels pixels = SkinTexturePixels.get(texture);
        if (pixels == null) {
            return;
        }
        renderer.render(matrices, vertexConsumers.getBuffer(VOXEL_LAYER), pixels, light, overlay);
    }

    private static void renderCuboid(MatrixStack matrices, VertexConsumer vertices, SkinTexturePixels pixels,
                                     int light, int overlay, int textureX, int textureY,
                                     float x, float y, float z, int width, int height, int depth) {
        renderCuboidSegment(matrices, vertices, pixels, light, overlay, textureX, textureY, 0,
                x, y, z, width, height, depth, true, true);
    }

    private static void renderCuboidSegment(MatrixStack matrices, VertexConsumer vertices, SkinTexturePixels pixels,
                                            int light, int overlay, int textureX, int textureY, int textureYSegment,
                                            float x, float y, float z, int width, int height, int depth,
                                            boolean renderMinYCap, boolean renderMaxYCap) {
        if (renderMinYCap) {
            renderFace(matrices, vertices, pixels, light, overlay, textureX + depth, textureY,
                    width, depth, new Point(x, y, z + depth), new Point(1.0F, 0.0F, 0.0F),
                    new Point(0.0F, 0.0F, -1.0F), new Point(0.0F, -1.0F, 0.0F));
        }
        if (renderMaxYCap) {
            renderFace(matrices, vertices, pixels, light, overlay, textureX + depth + width, textureY,
                    width, depth, new Point(x, y + height, z + depth), new Point(1.0F, 0.0F, 0.0F),
                    new Point(0.0F, 0.0F, -1.0F), new Point(0.0F, 1.0F, 0.0F));
        }

        int sideTextureY = textureY + depth + textureYSegment;
        renderFace(matrices, vertices, pixels, light, overlay, textureX, sideTextureY,
                depth, height, new Point(x, y, z + depth), new Point(0.0F, 0.0F, -1.0F),
                new Point(0.0F, 1.0F, 0.0F), new Point(-1.0F, 0.0F, 0.0F));
        renderFace(matrices, vertices, pixels, light, overlay, textureX + depth, sideTextureY,
                width, height, new Point(x, y, z), new Point(1.0F, 0.0F, 0.0F),
                new Point(0.0F, 1.0F, 0.0F), new Point(0.0F, 0.0F, -1.0F));
        renderFace(matrices, vertices, pixels, light, overlay, textureX + depth + width, sideTextureY,
                depth, height, new Point(x + width, y, z), new Point(0.0F, 0.0F, 1.0F),
                new Point(0.0F, 1.0F, 0.0F), new Point(1.0F, 0.0F, 0.0F));
        renderFace(matrices, vertices, pixels, light, overlay, textureX + depth + width + depth, sideTextureY,
                width, height, new Point(x + width, y, z + depth), new Point(-1.0F, 0.0F, 0.0F),
                new Point(0.0F, 1.0F, 0.0F), new Point(0.0F, 0.0F, 1.0F));
    }

    private static void renderFace(MatrixStack matrices, VertexConsumer vertices, SkinTexturePixels pixels,
                                   int light, int overlay, int textureX, int textureY, int width, int height,
                                   Point origin, Point uStep, Point vStep, Point normal) {
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int pixelX = textureX + col;
                int pixelY = textureY + row;
                int argb = pixels.getArgb(pixelX, pixelY);
                if (((argb >>> 24) & 0xFF) == 0) {
                    continue;
                }

                Point inner00 = origin.add(uStep.multiply(col)).add(vStep.multiply(row));
                Point inner10 = inner00.add(uStep);
                Point inner11 = inner10.add(vStep);
                Point inner01 = inner00.add(vStep);
                Point offset = normal.multiply(OUTER_THICKNESS);
                Point outer00 = inner00.add(offset);
                Point outer10 = inner10.add(offset);
                Point outer11 = inner11.add(offset);
                Point outer01 = inner01.add(offset);

                emitQuad(matrices, vertices, outer00, outer10, outer11, outer01, normal, argb, light, overlay);
                if (!isVisible(pixels, textureX, textureY, width, height, col, row - 1)) {
                    emitQuad(matrices, vertices, outer00, outer10, inner10, inner00, vStep.negate(), argb, light, overlay);
                }
                if (!isVisible(pixels, textureX, textureY, width, height, col + 1, row)) {
                    emitQuad(matrices, vertices, outer10, outer11, inner11, inner10, uStep, argb, light, overlay);
                }
                if (!isVisible(pixels, textureX, textureY, width, height, col, row + 1)) {
                    emitQuad(matrices, vertices, outer11, outer01, inner01, inner11, vStep, argb, light, overlay);
                }
                if (!isVisible(pixels, textureX, textureY, width, height, col - 1, row)) {
                    emitQuad(matrices, vertices, outer01, outer00, inner00, inner01, uStep.negate(), argb, light, overlay);
                }
            }
        }
    }

    private static boolean isVisible(SkinTexturePixels pixels, int textureX, int textureY, int width, int height,
                                    int col, int row) {
        if (col < 0 || row < 0 || col >= width || row >= height) {
            return false;
        }
        return ((pixels.getArgb(textureX + col, textureY + row) >>> 24) & 0xFF) != 0;
    }

    private static void emitQuad(MatrixStack matrices, VertexConsumer vertices,
                                 Point p0, Point p1, Point p2, Point p3, Point normal,
                                 int argb, int light, int overlay) {
        Point rp0 = toRenderPoint(p0);
        Point rp1 = toRenderPoint(p1);
        Point rp2 = toRenderPoint(p2);
        Point rp3 = toRenderPoint(p3);
        Point renderNormal = toRenderNormal(normal);
        if (rp1.subtract(rp0).cross(rp2.subtract(rp0)).dot(renderNormal) >= 0.0F) {
            emitVertex(matrices, vertices, rp0, renderNormal, argb, light, overlay);
            emitVertex(matrices, vertices, rp1, renderNormal, argb, light, overlay);
            emitVertex(matrices, vertices, rp2, renderNormal, argb, light, overlay);
            emitVertex(matrices, vertices, rp3, renderNormal, argb, light, overlay);
        } else {
            emitVertex(matrices, vertices, rp0, renderNormal, argb, light, overlay);
            emitVertex(matrices, vertices, rp3, renderNormal, argb, light, overlay);
            emitVertex(matrices, vertices, rp2, renderNormal, argb, light, overlay);
            emitVertex(matrices, vertices, rp1, renderNormal, argb, light, overlay);
        }
    }

    private static void emitVertex(MatrixStack matrices, VertexConsumer vertices, Point point, Point normal,
                                   int argb, int light, int overlay) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        int alpha = (argb >>> 24) & 0xFF;
        vertices.vertex(matrix, point.x(), point.y(), point.z())
                .color((argb >>> 16) & 0xFF, (argb >>> 8) & 0xFF, argb & 0xFF, alpha)
                .texture(0.0F, 0.0F)
                .overlay(overlay)
                .light(light)
                .normal(normal.x(), normal.y(), normal.z());
    }

    private static Point toRenderPoint(Point point) {
        return new Point(-point.x() / 16.0F, point.y() / 16.0F, point.z() / 16.0F);
    }

    private static Point toRenderNormal(Point normal) {
        return new Point(-normal.x(), normal.y(), normal.z());
    }

    @FunctionalInterface
    private interface PartRenderer {
        void render(MatrixStack matrices, VertexConsumer vertices, SkinTexturePixels pixels, int light, int overlay);

        static PartRenderer head() {
            return (matrices, vertices, pixels, light, overlay) ->
                    renderCuboid(matrices, vertices, pixels, light, overlay, 32, 0,
                            -4.0F, 16.0F, -4.0F, 8, 8, 8);
        }

        static PartRenderer torsoUpper() {
            return (matrices, vertices, pixels, light, overlay) ->
                    renderCuboidSegment(matrices, vertices, pixels, light, overlay, 16, 32, 0,
                            -4.0F, 18.0F, -2.0F, 8, 6, 4, false, true);
        }

        static PartRenderer torsoLower() {
            return (matrices, vertices, pixels, light, overlay) ->
                    renderCuboidSegment(matrices, vertices, pixels, light, overlay, 16, 32, 6,
                            -4.0F, 12.0F, -2.0F, 8, 6, 4, true, false);
        }

        static PartRenderer leftArmUpper(boolean slim) {
            int width = slim ? 3 : 4;
            float x = slim ? -7.0F : -8.0F;
            return (matrices, vertices, pixels, light, overlay) ->
                    renderCuboidSegment(matrices, vertices, pixels, light, overlay, 48, 48, 0,
                            x, 16.0F, -2.0F, width, 6, 4, false, true);
        }

        static PartRenderer leftArmLower(boolean slim) {
            int width = slim ? 3 : 4;
            float x = slim ? -7.0F : -8.0F;
            return (matrices, vertices, pixels, light, overlay) ->
                    renderCuboidSegment(matrices, vertices, pixels, light, overlay, 48, 48, 6,
                            x, 10.0F, -2.0F, width, 6, 4, true, false);
        }

        static PartRenderer rightArmUpper(boolean slim) {
            int width = slim ? 3 : 4;
            return (matrices, vertices, pixels, light, overlay) ->
                    renderCuboidSegment(matrices, vertices, pixels, light, overlay, 40, 32, 0,
                            4.0F, 16.0F, -2.0F, width, 6, 4, false, true);
        }

        static PartRenderer rightArmLower(boolean slim) {
            int width = slim ? 3 : 4;
            return (matrices, vertices, pixels, light, overlay) ->
                    renderCuboidSegment(matrices, vertices, pixels, light, overlay, 40, 32, 6,
                            4.0F, 10.0F, -2.0F, width, 6, 4, true, false);
        }

        static PartRenderer leftLegUpper() {
            return (matrices, vertices, pixels, light, overlay) ->
                    renderCuboidSegment(matrices, vertices, pixels, light, overlay, 0, 48, 0,
                            -4.0F, 6.0F, -2.0F, 4, 6, 4, false, true);
        }

        static PartRenderer leftLegLower() {
            return (matrices, vertices, pixels, light, overlay) ->
                    renderCuboidSegment(matrices, vertices, pixels, light, overlay, 0, 48, 6,
                            -4.0F, 0.0F, -2.0F, 4, 6, 4, true, false);
        }

        static PartRenderer rightLegUpper() {
            return (matrices, vertices, pixels, light, overlay) ->
                    renderCuboidSegment(matrices, vertices, pixels, light, overlay, 0, 32, 0,
                            0.0F, 6.0F, -2.0F, 4, 6, 4, false, true);
        }

        static PartRenderer rightLegLower() {
            return (matrices, vertices, pixels, light, overlay) ->
                    renderCuboidSegment(matrices, vertices, pixels, light, overlay, 0, 32, 6,
                            0.0F, 0.0F, -2.0F, 4, 6, 4, true, false);
        }
    }

    private record Point(float x, float y, float z) {
        Point add(Point other) {
            return new Point(x + other.x, y + other.y, z + other.z);
        }

        Point subtract(Point other) {
            return new Point(x - other.x, y - other.y, z - other.z);
        }

        Point multiply(float scalar) {
            return new Point(x * scalar, y * scalar, z * scalar);
        }

        Point negate() {
            return new Point(-x, -y, -z);
        }

        Point cross(Point other) {
            return new Point(
                    y * other.z - z * other.y,
                    z * other.x - x * other.z,
                    x * other.y - y * other.x
            );
        }

        float dot(Point other) {
            return x * other.x + y * other.y + z * other.z;
        }
    }
}
