package com.kuilunfuzhe.monvhua.features.forensics;

import net.minecraft.block.BlockState;
import net.minecraft.nbt.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import java.util.*;

/** A diameter-three sphere sampled by block centers. Whole blocks remain whole at the boundary. */
public final class EvidenceBlockSnapshot {
    public static final double RADIUS = 3;
    private EvidenceBlockSnapshot() {}
    public static List<BlockPos> positions(Vec3d center) {
        List<BlockPos> result=new ArrayList<>();
        BlockPos min=BlockPos.ofFloored(center.subtract(RADIUS,RADIUS,RADIUS));
        BlockPos max=BlockPos.ofFloored(center.add(RADIUS,RADIUS,RADIUS));
        for(BlockPos pos:BlockPos.iterate(min,max))
            if(Vec3d.ofCenter(pos).squaredDistanceTo(center)<=RADIUS*RADIUS+1e-9)result.add(pos.toImmutable());
        return List.copyOf(result);
    }
    public static NbtCompound capture(ServerWorld world,Vec3d origin) {
        NbtCompound result=new NbtCompound();NbtList blocks=new NbtList();int missing=0;
        for(BlockPos pos:positions(origin)) {
            if(!world.isChunkLoaded(pos)){missing++;continue;}
            BlockState state=world.getBlockState(pos);
            if(state.isAir())continue;
            NbtCompound entry=new NbtCompound();
            entry.putDouble("x",pos.getX()-origin.x);entry.putDouble("y",pos.getY()-origin.y);entry.putDouble("z",pos.getZ()-origin.z);
            BlockState.CODEC.encodeStart(NbtOps.INSTANCE,state).result().ifPresent(n->entry.put("state",n));
            if(entry.contains("state"))blocks.add(entry);
        }
        result.putDouble("radius",RADIUS);result.putLong("revision",world.getTime());
        result.putInt("missing_chunks",missing);result.put("blocks",blocks);
        return result;
    }
}
