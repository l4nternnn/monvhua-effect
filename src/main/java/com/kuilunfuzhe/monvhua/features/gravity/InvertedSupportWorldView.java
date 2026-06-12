package com.kuilunfuzhe.monvhua.features.gravity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.WorldView;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.ColorResolver;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.light.LightingProvider;
import net.minecraft.world.dimension.DimensionType;

import java.util.List;

public final class InvertedSupportWorldView implements WorldView {
    private final WorldView delegate;
    private final BlockPos anchor;

    public InvertedSupportWorldView(WorldView delegate, BlockPos anchor) {
        this.delegate = delegate;
        this.anchor = anchor.toImmutable();
    }

    public static Direction flipDirection(BlockView world, Direction direction) {
        if (world instanceof InvertedSupportWorldView && direction.getAxis() == Direction.Axis.Y) {
            return direction.getOpposite();
        }
        return direction;
    }

    public static Direction flipVertical(Direction direction) {
        return direction.getAxis() == Direction.Axis.Y ? direction.getOpposite() : direction;
    }

    public static BlockPos offsetFromFlippedSurface(BlockPos pos, Direction direction) {
        return pos.offset(flipVertical(direction));
    }

    private BlockPos mirror(BlockPos pos) {
        int mirroredY = anchor.getY() * 2 - pos.getY();
        return new BlockPos(pos.getX(), mirroredY, pos.getZ());
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return delegate.getBlockEntity(mirror(pos));
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return delegate.getBlockState(mirror(pos));
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return delegate.getFluidState(mirror(pos));
    }

    @Override
    public int getHeight() {
        return delegate.getHeight();
    }

    @Override
    public int getBottomY() {
        return delegate.getBottomY();
    }

    @Override
    public float getBrightness(Direction direction, boolean shaded) {
        return delegate.getBrightness(flipDirection(this, direction), shaded);
    }

    @Override
    public LightingProvider getLightingProvider() {
        return delegate.getLightingProvider();
    }

    @Override
    public int getColor(BlockPos pos, ColorResolver colorResolver) {
        return delegate.getColor(mirror(pos), colorResolver);
    }

    @Override
    public WorldBorder getWorldBorder() {
        return delegate.getWorldBorder();
    }

    @Override
    public BlockView getChunkAsView(int chunkX, int chunkZ) {
        return delegate.getChunkAsView(chunkX, chunkZ);
    }

    @Override
    public List<VoxelShape> getEntityCollisions(Entity entity, Box box) {
        return delegate.getEntityCollisions(entity, box);
    }

    @Override
    public Chunk getChunk(int chunkX, int chunkZ, ChunkStatus leastStatus, boolean create) {
        return delegate.getChunk(chunkX, chunkZ, leastStatus, create);
    }

    @Override
    public boolean isChunkLoaded(int chunkX, int chunkZ) {
        return delegate.isChunkLoaded(chunkX, chunkZ);
    }

    @Override
    public int getTopY(Heightmap.Type heightmap, int x, int z) {
        return delegate.getTopY(heightmap, x, z);
    }

    @Override
    public int getAmbientDarkness() {
        return delegate.getAmbientDarkness();
    }

    @Override
    public BiomeAccess getBiomeAccess() {
        return delegate.getBiomeAccess();
    }

    @Override
    public RegistryEntry<Biome> getGeneratorStoredBiome(int biomeX, int biomeY, int biomeZ) {
        return delegate.getGeneratorStoredBiome(biomeX, biomeY, biomeZ);
    }

    @Override
    public boolean isClient() {
        return delegate.isClient();
    }

    @Override
    public int getSeaLevel() {
        return delegate.getSeaLevel();
    }

    @Override
    public DimensionType getDimension() {
        return delegate.getDimension();
    }

    @Override
    public DynamicRegistryManager getRegistryManager() {
        return delegate.getRegistryManager();
    }

    @Override
    public FeatureSet getEnabledFeatures() {
        return delegate.getEnabledFeatures();
    }
}
