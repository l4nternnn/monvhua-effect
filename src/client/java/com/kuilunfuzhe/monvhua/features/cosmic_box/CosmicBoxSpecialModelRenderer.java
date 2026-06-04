package com.kuilunfuzhe.monvhua.features.cosmic_box;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.LoadedEntityModels;
import net.minecraft.client.render.item.model.special.SpecialModelRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Set;

public final class CosmicBoxSpecialModelRenderer implements SpecialModelRenderer<Void> {
    public CosmicBoxSpecialModelRenderer(LoadedEntityModels entityModels) {
    }

    @Override
    public void render(@Nullable Void data, ItemDisplayContext displayContext, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay, boolean leftHanded) {
        matrices.push();
        VertexConsumer vertices = vertexConsumers.getBuffer(CosmicBoxRenderLayers.cosmicBox());
        renderCube(matrices.peek().getPositionMatrix(), vertices);
        matrices.pop();
    }

    @Override
    public void collectVertices(Set<Vector3f> vertices) {
        vertices.add(new Vector3f(0.0F, 0.0F, 0.0F));
        vertices.add(new Vector3f(1.0F, 0.0F, 0.0F));
        vertices.add(new Vector3f(1.0F, 1.0F, 0.0F));
        vertices.add(new Vector3f(0.0F, 1.0F, 0.0F));
        vertices.add(new Vector3f(0.0F, 0.0F, 1.0F));
        vertices.add(new Vector3f(1.0F, 0.0F, 1.0F));
        vertices.add(new Vector3f(1.0F, 1.0F, 1.0F));
        vertices.add(new Vector3f(0.0F, 1.0F, 1.0F));
    }

    @Nullable
    @Override
    public Void getData(ItemStack stack) {
        return null;
    }

    private static void renderCube(Matrix4f matrix, VertexConsumer vertices) {
        quad(matrix, vertices, 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F, 0.0F, 1.0F, 1.0F);
        quad(matrix, vertices, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 1.0F, 1.0F, 0.0F);
        quad(matrix, vertices, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 0.0F, 0.0F, 1.0F, 0.0F);
        quad(matrix, vertices, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 1.0F, 0.0F, 1.0F, 0.0F, 0.0F, 1.0F);
        quad(matrix, vertices, 1.0F, 0.0F, 1.0F, 1.0F, 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, 1.0F, 1.0F, 1.0F);
        quad(matrix, vertices, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 1.0F, 1.0F, 0.0F, 1.0F, 0.0F);
    }

    private static void quad(Matrix4f matrix, VertexConsumer vertices,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float x4, float y4, float z4) {
        vertices.vertex(matrix, x1, y1, z1);
        vertices.vertex(matrix, x2, y2, z2);
        vertices.vertex(matrix, x3, y3, z3);
        vertices.vertex(matrix, x4, y4, z4);
    }

    public static final class Unbaked implements SpecialModelRenderer.Unbaked {
        public static final MapCodec<Unbaked> CODEC = MapCodec.unit(Unbaked::new);

        @Override
        public SpecialModelRenderer<?> bake(LoadedEntityModels entityModels) {
            return new CosmicBoxSpecialModelRenderer(entityModels);
        }

        @Override
        public MapCodec<Unbaked> getCodec() {
            return CODEC;
        }
    }
}
