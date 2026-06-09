package com.kuilunfuzhe.monvhua.features.gravity;

import net.minecraft.util.math.DirectionTransformation;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class InvertedBlockShapeTransform {
    private static final Map<VoxelShape, VoxelShape> MIRRORED_SHAPES = new ConcurrentHashMap<>();

    private InvertedBlockShapeTransform() {
    }

    public static boolean shouldMirror(BlockView world, BlockPos pos) {
        return InvertedBlockContext.shouldMirror(world, pos);
    }

    public static VoxelShape mirror(VoxelShape shape) {
        if (shape == null || shape.isEmpty() || shape == VoxelShapes.fullCube()) {
            return shape;
        }
        return MIRRORED_SHAPES.computeIfAbsent(shape, key -> VoxelShapes.transform(key, DirectionTransformation.INVERT_Y).simplify());
    }
}
