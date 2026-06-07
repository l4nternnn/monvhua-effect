package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.gravity.GravityMagic;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer", remap = false)
public abstract class SodiumInvertedBlockTextureMixin {
    @Unique
    private static final int[] MONVHUA_REVERSED_WINDING = {0, 3, 2, 1};

    @Unique
    private BlockPos monvhua$blockPos;

    @Inject(method = "renderModel", at = @At("HEAD"), remap = false)
    private void monvhua$captureBlockPos(BlockStateModel model, BlockState state, BlockPos pos, BlockPos origin, CallbackInfo ci) {
        monvhua$blockPos = pos;
    }

    @Inject(method = "renderModel", at = @At("RETURN"), remap = false)
    private void monvhua$clearBlockPos(BlockStateModel model, BlockState state, BlockPos pos, BlockPos origin, CallbackInfo ci) {
        monvhua$blockPos = null;
    }

    @Inject(method = "bufferQuad", at = @At("HEAD"), remap = false)
    private void monvhua$mirrorInvertedAreaBlockQuad(@Coerce MutableQuadView quad, float[] brightnesses, @Coerce Object material, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        BlockPos pos = monvhua$blockPos;
        if (client.world == null || pos == null || !GravityMagic.isInInvertedArea(client.world.getRegistryKey(), Vec3d.ofCenter(pos))) {
            return;
        }

        Direction originalFace = monvhua$quadFace(quad);
        boolean horizontalFace = originalFace != null && originalFace.getAxis() == Direction.Axis.Y;
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

        if (!Float.isFinite(minU) || !Float.isFinite(maxU) || !Float.isFinite(minV) || !Float.isFinite(maxV)) {
            return;
        }

        float sumU = minU + maxU;
        float sumV = minV + maxV;
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
            y[vertex] = 1.0F - quad.y(vertex);
            z[vertex] = quad.z(vertex);
            u[vertex] = horizontalFace ? sumU - quad.u(vertex) : quad.u(vertex);
            v[vertex] = horizontalFace ? sumV - quad.v(vertex) : quad.v(vertex);
            color[vertex] = quad.color(vertex);
            light[vertex] = quad.lightmap(vertex);
            hasNormal[vertex] = quad.hasNormal(vertex);
            normalX[vertex] = hasNormal[vertex] ? quad.normalX(vertex) : 0.0F;
            normalY[vertex] = hasNormal[vertex] ? -quad.normalY(vertex) : 0.0F;
            normalZ[vertex] = hasNormal[vertex] ? quad.normalZ(vertex) : 0.0F;
        }

        for (int vertex = 0; vertex < 4; vertex++) {
            int source = MONVHUA_REVERSED_WINDING[vertex];
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
            quad.cullFace(monvhua$flipVertical(quad.cullFace()));
        }
        if (quad.nominalFace() != null) {
            quad.nominalFace(monvhua$flipVertical(quad.nominalFace()));
        }
    }

    @Unique
    private static Direction monvhua$quadFace(MutableQuadView quad) {
        if (quad.cullFace() != null) {
            return quad.cullFace();
        }
        if (quad.nominalFace() != null) {
            return quad.nominalFace();
        }
        return quad.lightFace();
    }

    @Unique
    private static Direction monvhua$flipVertical(Direction direction) {
        if (direction == Direction.UP) {
            return Direction.DOWN;
        }
        if (direction == Direction.DOWN) {
            return Direction.UP;
        }
        return direction;
    }
}
