package com.kuilunfuzhe.monvhua.features.forensics.viewport;

import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.nbt.*;
import net.minecraft.util.math.*;
import net.minecraft.world.EmptyBlockView;
import java.util.*;

/** Small immutable block scene, independent of live chunks and block-entity contents. */
public final class EvidenceBlocks implements EvidenceEnvironmentLayer {
    private record Block(BlockState state,Vec3d offset) {}
    private final List<Block> blocks;
    private final List<Box> occluders;
    private final Box bounds;
    private final long revision;
    private EvidenceBlocks(NbtCompound snapshot) {
        revision=snapshot.getLong("revision",0);
        List<Block> entries=new ArrayList<>();List<Box> shapes=new ArrayList<>();Box extent=null;
        for(var element:snapshot.getListOrEmpty("blocks")) {
            if(entries.size()>=64)break;
            if(!(element instanceof NbtCompound entry)||!entry.contains("state"))continue;
            var state=BlockState.CODEC.parse(NbtOps.INSTANCE,entry.get("state")).result().orElse(Blocks.AIR.getDefaultState());
            // Fluid and dynamic block-entity renderers require a dedicated snapshot adapter.
            if(state.getRenderType()!=BlockRenderType.MODEL)continue;
            Vec3d offset=new Vec3d(entry.getDouble("x",0),entry.getDouble("y",0),entry.getDouble("z",0));
            if(!Double.isFinite(offset.lengthSquared())||offset.lengthSquared()>25)continue;
            entries.add(new Block(state,offset));
            Box box=new Box(offset,offset.add(1,1,1));extent=extent==null?box:extent.union(box);
            if(state.isOpaque())for(Box shape:state.getOutlineShape(EmptyBlockView.INSTANCE,BlockPos.ORIGIN).getBoundingBoxes())shapes.add(shape.offset(offset));
        }
        blocks=List.copyOf(entries);occluders=List.copyOf(shapes);bounds=extent==null?new Box(0,0,0,0,0,0):extent;
    }
    public static EvidenceBlocks read(NbtCompound snapshot){return snapshot.contains("blocks")?new EvidenceBlocks(snapshot):null;}
    @Override public long revision(){return revision;}
    @Override public Box bounds(){return bounds;}
    @Override public boolean occludes(Vec3d from,Vec3d to){return occluders.stream().anyMatch(b->b.contains(from)||b.raycast(from,to).isPresent());}
    @Override public void render(MatrixStack matrices,VertexConsumerProvider.Immediate consumers){
        var manager=MinecraftClient.getInstance().getBlockRenderManager();
        for(Block block:blocks){
            matrices.push();matrices.translate(block.offset.x,block.offset.y,block.offset.z);
            manager.renderBlockAsEntity(block.state,matrices,consumers,15728880,OverlayTexture.DEFAULT_UV);
            matrices.pop();
        }
    }
}
