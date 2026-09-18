package com.kuilunfuzhe.monvhua.features.swing;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.LightType;
import net.minecraft.world.biome.ColorResolver;
import net.minecraft.world.chunk.light.LightingProvider;

/** Local neighbors for model/AO queries, transformed world coordinates for light and biome tint. */
public record SwingRenderWorld(SwingStructureSpace space, BlockRenderView parent) implements BlockRenderView {
    private BlockPos worldPos(BlockPos local) { return BlockPos.ofFloored(space.toWorld(Vec3d.of(local))); }
    @Override public BlockState getBlockState(BlockPos pos) { return space.structure().blockWorld().getBlockState(pos); }
    @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
    @Override public BlockEntity getBlockEntity(BlockPos pos) { return null; }
    @Override public int getHeight() { return space.structure().blockWorld().getHeight(); }
    @Override public int getBottomY() { return space.structure().blockWorld().getBottomY(); }
    @Override public LightingProvider getLightingProvider() { return parent.getLightingProvider(); }
    @Override public int getLightLevel(LightType type, BlockPos pos) {
        return Math.max(type == LightType.BLOCK ? getBlockState(pos).getLuminance() : 0,
                parent.getLightLevel(type, worldPos(pos)));
    }
    @Override public int getBaseLightLevel(BlockPos pos, int ambientDarkness) { return parent.getBaseLightLevel(worldPos(pos), ambientDarkness); }
    @Override public int getColor(BlockPos pos, ColorResolver resolver) { return parent.getColor(worldPos(pos), resolver); }
    @Override public float getBrightness(Direction direction, boolean shaded) {
        if (!shaded) return parent.getBrightness(direction, false);
        Vec3d normal = SwingTransform.rotate(Vec3d.of(direction.getVector()), space.angle(), space.zAxis());
        // Blend world-axis face lighting continuously as the structure rotates.
        double x = normal.x * normal.x, y = normal.y * normal.y, z = normal.z * normal.z;
        return (float) (x * parent.getBrightness(normal.x < 0 ? Direction.WEST : Direction.EAST, true)
                + y * parent.getBrightness(normal.y < 0 ? Direction.DOWN : Direction.UP, true)
                + z * parent.getBrightness(normal.z < 0 ? Direction.NORTH : Direction.SOUTH, true));
    }
}
