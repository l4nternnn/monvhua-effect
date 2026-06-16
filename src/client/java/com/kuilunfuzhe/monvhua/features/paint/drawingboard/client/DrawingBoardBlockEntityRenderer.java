package com.kuilunfuzhe.monvhua.features.paint.drawingboard.client;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.kuilunfuzhe.monvhua.features.paint.drawingboard.DrawingBoardBlock;
import com.kuilunfuzhe.monvhua.features.paint.drawingboard.DrawingBoardBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

public class DrawingBoardBlockEntityRenderer implements BlockEntityRenderer<DrawingBoardBlockEntity> {
    private static final float MIN_X = 3.0F / 16.0F;
    private static final float MAX_X = 13.0F / 16.0F;
    private static final float MIN_Y = 10.25607F / 16.0F;
    private static final float MAX_Y = 25.25607F / 16.0F;
    private static final float CANVAS_Z = 6.50F / 16.0F;
    private static final float PIVOT_X = 8.0F / 16.0F;
    private static final float PIVOT_Y = 15.30558F / 16.0F;
    private static final float PIVOT_Z = 8.58584F / 16.0F;
    private static final Map<Long, TextureEntry> TEXTURES = new HashMap<>();

    public DrawingBoardBlockEntityRenderer(BlockEntityRendererFactory.Context context) {
    }

    @Override
    public void render(DrawingBoardBlockEntity entity, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay, Vec3d cameraPos) {
        BlockState state = entity.getCachedState();
        if (!state.contains(DrawingBoardBlock.FACING)) {
            return;
        }
        TextureEntry entry = textureFor(entity);
        matrices.push();
        matrices.translate(0.5F, 0.0F, 0.5F);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yawFor(state.get(DrawingBoardBlock.FACING))));
        matrices.translate(-0.5F, 0.0F, -0.5F);
        matrices.translate(PIVOT_X, PIVOT_Y, PIVOT_Z);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(10.0F));
        matrices.translate(-PIVOT_X, -PIVOT_Y, -PIVOT_Z);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        VertexConsumer vertices = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(entry.id));
        vertex(vertices, matrix, MIN_X, MAX_Y, CANVAS_Z, 1.0F, 0.0F);
        vertex(vertices, matrix, MAX_X, MAX_Y, CANVAS_Z, 0.0F, 0.0F);
        vertex(vertices, matrix, MAX_X, MIN_Y, CANVAS_Z, 0.0F, 1.0F);
        vertex(vertices, matrix, MIN_X, MIN_Y, CANVAS_Z, 1.0F, 1.0F);
        matrices.pop();
    }

    private static TextureEntry textureFor(DrawingBoardBlockEntity entity) {
        long key = entity.getPos().asLong();
        TextureEntry entry = TEXTURES.get(key);
        if (entry == null) {
            Identifier id = Identifier.of(MonvhuaMod.MOD_ID, "dynamic/drawing_board/" + key);
            entry = new TextureEntry(id);
            TEXTURES.put(key, entry);
        }
        if (entry.version != entity.getVersion()) {
            entry.upload(entity.copyPixels());
            entry.version = entity.getVersion();
        }
        return entry;
    }

    private static float yawFor(Direction facing) {
        return switch (facing) {
            case EAST -> 90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 270.0F;
            default -> 0.0F;
        };
    }

    private static void vertex(VertexConsumer vertices, Matrix4f matrix, float x, float y, float z, float u, float v) {
        vertices.vertex(matrix, x, y, z)
                .color(255, 255, 255, 255)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(0.0F, 0.0F, -1.0F);
    }

    private static final class TextureEntry {
        private final Identifier id;
        private NativeImageBackedTexture texture;
        private int version = -1;

        private TextureEntry(Identifier id) {
            this.id = id;
        }

        private void upload(int[] pixels) {
            NativeImage image = new NativeImage(DrawingBoardBlockEntity.WIDTH, DrawingBoardBlockEntity.HEIGHT, false);
            for (int y = 0; y < DrawingBoardBlockEntity.HEIGHT; y++) {
                for (int x = 0; x < DrawingBoardBlockEntity.WIDTH; x++) {
                    image.setColorArgb(x, y, pixels[y * DrawingBoardBlockEntity.WIDTH + x]);
                }
            }
            if (texture == null) {
                texture = new NativeImageBackedTexture(() -> "monvhua drawing board " + id, image);
                MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
            } else {
                texture.setImage(image);
                texture.upload();
            }
        }
    }
}
