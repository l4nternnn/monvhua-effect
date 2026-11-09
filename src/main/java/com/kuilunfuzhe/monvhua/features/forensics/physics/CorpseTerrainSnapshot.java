package com.kuilunfuzhe.monvhua.features.forensics.physics;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import java.util.*;

/** Server-thread-only capture. Order and coordinates are stable for equality-based invalidation. */
public final class CorpseTerrainSnapshot {
    private CorpseTerrainSnapshot() {}
    public static List<CorpsePhysicsBridge.Box> capture(ServerWorld world,Vec3d origin) {
        long start=System.nanoTime();
        try{return captureBoxes(world,origin);}finally{CorpsePhysicsDiagnostics.terrainCapture(System.nanoTime()-start);}
    }
    private static List<CorpsePhysicsBridge.Box> captureBoxes(ServerWorld world,Vec3d origin){
        var result=new ArrayList<CorpsePhysicsBridge.Box>();
        for(BlockPos pos:BlockPos.iterate(BlockPos.ofFloored(origin.add(-4,-4,-4)),BlockPos.ofFloored(origin.add(4,6,4)))) {
            if(!world.isChunkLoaded(pos))throw new IllegalArgumentException("采样区域区块尚未加载");
            var state=world.getBlockState(pos);
            if(!state.getFluidState().isEmpty())throw new IllegalArgumentException("初版不支持采样区域内液体");
            for(Box local:state.getCollisionShape(world,pos).getBoundingBoxes()) {
                Box b=local.offset(pos).offset(origin.negate());
                float x0=(float)Math.max(-4,b.minX),y0=(float)Math.max(-4,b.minY),z0=(float)Math.max(-4,b.minZ);
                float x1=(float)Math.min(4,b.maxX),y1=(float)Math.min(6,b.maxY),z1=(float)Math.min(4,b.maxZ);
                if(x1<=x0||y1<=y0||z1<=z0)continue;
                result.add(new CorpsePhysicsBridge.Box(x0,y0,z0,x1,y1,z1));
                if(result.size()>2048)throw new IllegalArgumentException("碰撞形状超过 2048，保留原尸体");
            }
        }
        return List.copyOf(result);
    }
}
