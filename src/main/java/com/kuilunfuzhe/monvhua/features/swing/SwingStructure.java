package com.kuilunfuzhe.monvhua.features.swing;

import com.mojang.serialization.Codec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import java.util.List;
import net.minecraft.util.math.Box;

public record SwingStructure(List<SwingBlock> blocks) {
    public static final int MAX_BLOCKS = 512;
    public SwingStructure { blocks = List.copyOf(blocks); }
    public List<SwingBlock> seatBlocks() { return blocks.stream().filter(b -> SwingBlockRoles.seat(b.state())).toList(); }
    public List<SwingBlock> backrestBlocks() { return blocks.stream().filter(b -> SwingBlockRoles.backrest(b.state()) && !SwingBlockRoles.seat(b.state())).toList(); }
    public Box localBounds() {
        if (blocks.isEmpty()) return new Box(0, 0, 0, 1, 1, 1);
        int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,maxY=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;
        for (SwingBlock b: blocks) { BlockPos p=b.localPos(); minX=Math.min(minX,p.getX()); minY=Math.min(minY,p.getY()); minZ=Math.min(minZ,p.getZ()); maxX=Math.max(maxX,p.getX()+1); maxY=Math.max(maxY,p.getY()+1); maxZ=Math.max(maxZ,p.getZ()+1); }
        return new Box(minX,minY,minZ,maxX,maxY,maxZ).offset(-.5, -.5, -.5);
    }
    /** Center of the lowest captured horizontal row, used as the only seat anchor. */
    public BlockPos seatLocal() {
        List<SwingBlock> seats = seatBlocks();
        int minY = seats.stream().mapToInt(b -> b.localPos().getY()).min().orElse(-2);
        java.util.List<SwingBlock> row = seats.stream().filter(b -> b.localPos().getY() == minY).toList();
        int minX = row.stream().mapToInt(b -> b.localPos().getX()).min().orElse(0);
        int maxX = row.stream().mapToInt(b -> b.localPos().getX()).max().orElse(0);
        return new BlockPos((minX + maxX) / 2, minY, 0);
    }
    public Box seatBounds() {
        return seatShapes().stream().reduce(Box::union).orElse(new Box(0, 0, 0, 0, 0, 0));
    }
    /** Shapes use the same block-center origin as the renderer. Only the lowest seat row is rideable. */
    public List<Box> seatShapes() {
        int bottom = seatBlocks().stream().mapToInt(b -> b.localPos().getY()).min().orElse(Integer.MIN_VALUE);
        return seatBlocks().stream().filter(b -> b.localPos().getY() == bottom).flatMap(b ->
                b.state().getOutlineShape(net.minecraft.world.EmptyBlockView.INSTANCE, b.localPos()).getBoundingBoxes().stream()
                        .map(box -> box.offset(b.localPos().getX() - .5, b.localPos().getY() - .5, b.localPos().getZ() - .5))).toList();
    }
    /** One stable rideable slot per seat block in the lowest seat row. */
    public List<Box> seatSlots() {
        int bottom = seatBlocks().stream().mapToInt(b -> b.localPos().getY()).min().orElse(Integer.MIN_VALUE);
        return seatBlocks().stream().filter(b -> b.localPos().getY() == bottom).map(b ->
                b.state().getOutlineShape(net.minecraft.world.EmptyBlockView.INSTANCE, b.localPos()).getBoundingBoxes().stream()
                        .map(box -> box.offset(b.localPos().getX() - .5, b.localPos().getY() - .5, b.localPos().getZ() - .5))
                        .reduce(Box::union).orElse(null)).filter(java.util.Objects::nonNull).toList();
    }
    public java.util.List<Long> packedPositions() { return blocks.stream().map(b -> b.localPos().asLong()).toList(); }
    public java.util.List<Integer> stateIds() { return blocks.stream().map(b -> Block.getRawIdFromState(b.state())).toList(); }
    public static SwingStructure fromPacked(java.util.List<Long> positions, java.util.List<Integer> ids) {
        java.util.ArrayList<SwingBlock> out = new java.util.ArrayList<>();
        int count = Math.min(positions.size(), ids.size());
        for (int i = 0; i < Math.min(count, MAX_BLOCKS); i++) {
            BlockState state = Block.getStateFromRawId(ids.get(i));
            if (state != null && !state.isAir()) out.add(new SwingBlock(BlockPos.fromLong(positions.get(i)), state));
        }
        return new SwingStructure(out);
    }
    public static SwingStructure capture(net.minecraft.world.World world, BlockPos min, BlockPos max, BlockPos pivot) {
        java.util.ArrayList<SwingBlock> out = new java.util.ArrayList<>();
        for (BlockPos p : BlockPos.iterate(min, max)) {
            BlockState state = world.getBlockState(p);
            if (!state.isAir() && state.getBlock() != net.minecraft.block.Blocks.BEDROCK && world.getBlockEntity(p) == null)
                out.add(new SwingBlock(p.subtract(pivot), state));
            if (out.size() > MAX_BLOCKS) throw new IllegalArgumentException("Swing selection exceeds " + MAX_BLOCKS + " blocks");
        }
        return new SwingStructure(out);
    }
}
