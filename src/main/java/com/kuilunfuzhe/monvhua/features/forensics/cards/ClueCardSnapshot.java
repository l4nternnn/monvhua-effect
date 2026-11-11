package com.kuilunfuzhe.monvhua.features.forensics.cards;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;

/** Fixed-view block-surface raster. No live chunks, NBT, absolute coordinates or hidden faces are sent. */
public final class ClueCardSnapshot {
    public static final int WIDTH=64,HEIGHT=48;
    public static final double RADIUS=1.5;
    private ClueCardSnapshot(){}
    public static NbtCompound capture(ServerPlayerEntity player,Vec3d center){
        return capture(player,center,player.getEyePos());
    }
    public static NbtCompound capture(ServerPlayerEntity player,Vec3d center,Vec3d eye){
        var world=((net.minecraft.server.world.ServerWorld)player.getWorld());Vec3d forward=center.subtract(eye).normalize();
        Vec3d right=forward.crossProduct(new Vec3d(0,1,0));if(right.lengthSquared()<.001)right=new Vec3d(1,0,0);right=right.normalize();
        Vec3d up=right.crossProduct(forward).normalize();double distance=eye.distanceTo(center);
        double tangent=Math.min(1.8,RADIUS/Math.max(.5,distance-RADIUS*.35));
        int[] colors=new int[WIDTH*HEIGHT];int visible=0;
        for(int y=0;y<HEIGHT;y++)for(int x=0;x<WIDTH;x++){
            Vec3d direction=forward.add(right.multiply((2*(x+.5)/WIDTH-1)*tangent)).add(up.multiply((1-2*(y+.5)/HEIGHT)*tangent*HEIGHT/WIDTH)).normalize();
            Vec3d far=eye.add(direction.multiply(distance+RADIUS+1));
            // Never force a chunk load along a ray, including the path before the sampled sphere.
            boolean loaded=true;for(double d=0;d<distance+RADIUS+1;d+=.5)if(!world.isChunkLoaded(BlockPos.ofFloored(eye.add(direction.multiply(d))))){loaded=false;break;}
            if(!loaded)continue;
            var hit=world.raycast(new RaycastContext(eye,far,RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,player));
            if(hit.getType()!=HitResult.Type.BLOCK||hit.getPos().squaredDistanceTo(center)>RADIUS*RADIUS+.001)continue;
            var state=world.getBlockState(hit.getBlockPos());int color=state.getMapColor(world,hit.getBlockPos()).color;
            if(color==0)color=0x777777;
            float light=switch(hit.getSide()){case UP->1f;case DOWN->.5f;case NORTH,SOUTH->.72f;case EAST,WEST->.85f;};
            int r=(int)((color>>16&255)*light),g=(int)((color>>8&255)*light),b=(int)((color&255)*light);
            colors[y*WIDTH+x]=0xff000000|(r<<16)|(g<<8)|b;visible++;
        }
        if(visible==0)throw new IllegalArgumentException("未记录到可见方块表面，请调整位置");
        var n=new NbtCompound();n.putInt("width",WIDTH);n.putInt("height",HEIGHT);n.putIntArray("pixels",colors);
        n.putString("kind","block_surface_raster_v1");return n;
    }
}
