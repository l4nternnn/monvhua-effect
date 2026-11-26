package com.kuilunfuzhe.monvhua.features.forensics.butterfly;

import net.minecraft.world.World;
import net.minecraft.util.math.*;

/** Conservative small-box sweeps. Every touched chunk is checked before block/collision access. */
public final class ButterflyClearance {
    public static final double RADIUS=.10;
    private ButterflyClearance(){}
    public static boolean clear(World world,Vec3d from,Vec3d to){
        return clear(world,from,to,()->{});
    }
    public static boolean clear(World world,Vec3d from,Vec3d to,Runnable charge){
        if(!finite(from)||!finite(to)||from.squaredDistanceTo(to)>1024)return false;
        int steps=Math.max(1,(int)Math.ceil(from.distanceTo(to)/.20));
        Vec3d previous=from;
        for(int i=1;i<=steps;i++){
            charge.run();
            Vec3d next=from.lerp(to,(double)i/steps);
            Box box=new Box(previous,next).expand(RADIUS);
            if(box.minY<world.getBottomY()||box.maxY>=world.getTopYInclusive())return false;
            for(int x=MathHelper.floor(box.minX-1)>>4;x<=MathHelper.floor(box.maxX+1)>>4;x++)
                for(int z=MathHelper.floor(box.minZ-1)>>4;z<=MathHelper.floor(box.maxZ+1)>>4;z++)
                    if(!world.isChunkLoaded(new BlockPos(x<<4,MathHelper.floor(box.minY),z<<4)))return false;
            if(!world.getWorldBorder().contains(box))return false;
            // Fences/walls can extend beyond their owning block; include neighbouring shapes too.
            for(BlockPos p:BlockPos.iterate(MathHelper.floor(box.minX)-1,MathHelper.floor(box.minY)-1,MathHelper.floor(box.minZ)-1,
                    MathHelper.floor(box.maxX)+1,MathHelper.floor(box.maxY)+1,MathHelper.floor(box.maxZ)+1)){
                var state=world.getBlockState(p);
                if((!state.getFluidState().isEmpty()||state.isIn(net.minecraft.registry.tag.BlockTags.FIRE)
                        ||state.isOf(net.minecraft.block.Blocks.MAGMA_BLOCK))&&new Box(p).intersects(box))return false;
                for(var obstacle:state.getCollisionShape(world,p).getBoundingBoxes())
                    if(obstacle.offset(p).intersects(box))return false;
            }
            previous=next;
        }
        return true;
    }
    public static boolean finite(Vec3d p){return Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z);}
}
