package com.kuilunfuzhe.monvhua.features.swing;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import java.util.ArrayList;
/** Geometry-based detector: searches chain pairs below the clicked beam without scanning walls/ground. */
public final class SwingAssemblyDetector {
 private SwingAssemblyDetector() {}
 public static Result detect(ServerWorld world, BlockPos pivot) {
  return detect(world, pivot, new SwingDiagnostics());
 }
 public static Result detect(ServerWorld world, BlockPos pivot, SwingDiagnostics trace) {
  trace.log("DETECT_BEGIN " + SwingDiagnostics.describe(world,pivot));
  for (boolean zAxis : new boolean[]{false,true}) for(int distance=1;distance<=5;distance++) for(int length=2;length<=7;length++) {
   for(int side=-1;side<=1;side+=2) {
    int axis=side*distance; boolean ok=true;
    for(int y=1;y<=length;y++){ BlockPos p=offset(pivot,axis,-y,zAxis); BlockState s=world.getBlockState(p); if(!isChain(s)){trace.reject(world,pivot,p,zAxis,"rope-first");ok=false;break;} }
    if(!ok) continue;
    int other=side==1?-distance:distance;
    for(int y=1;y<=length;y++){ BlockPos p=offset(pivot,other,-y,zAxis); if(!isChain(world.getBlockState(p))){trace.reject(world,pivot,p,zAxis,"rope-second");ok=false;break;} }
    if(!ok) continue;
    BlockPos first=offset(pivot,-distance,-length-1,zAxis), second=offset(pivot,distance,-length-1,zAxis);
    if(!isSolid(world,first)){trace.reject(world,pivot,first,zAxis,"seat-left");continue;}
    if(!isSolid(world,second)){trace.reject(world,pivot,second,zAxis,"seat-right");continue;}
    ArrayList<SwingBlock> blocks=new ArrayList<>();
    for(int sideX:new int[]{-1,1}) for(int y=1;y<=length;y++){ int a=sideX*distance; BlockPos p=offset(pivot,a,-y,zAxis); blocks.add(new SwingBlock(local(a,-y,zAxis),world.getBlockState(p))); }
    boolean seatComplete=true;
    for(int a=-distance;a<=distance;a++){ BlockPos p=offset(pivot,a,-length-1,zAxis); if(!isSolid(world,p)){trace.reject(world,pivot,p,zAxis,"seat-gap");seatComplete=false;break;} blocks.add(new SwingBlock(local(a,-length-1,zAxis),world.getBlockState(p))); }
    if(!seatComplete) continue;
    // Capture complete backrest rows on either side of the seat; fixed beam is above this row.
    int backY = -length;
    int backrestCandidates = 0;
    for (int perp : new int[]{-1, 1}) {
      ArrayList<SwingBlock> candidate = new ArrayList<>();
      for (int a=-distance; a<=distance; a++) {
        BlockPos bp = zAxis ? pivot.add(perp, backY, a) : pivot.add(a, backY, perp);
        BlockState bs = world.getBlockState(bp);
        if (!SwingBlockRoles.backrest(bs) || SwingBlockRoles.seat(bs) || world.getBlockEntity(bp)!=null) { candidate.clear(); break; }
        candidate.add(new SwingBlock(localBack(a, backY, zAxis, perp), bs));
      }
      if (candidate.size() == distance * 2 + 1) {
        blocks.addAll(candidate);
        backrestCandidates++;
      }
    }
    trace.log("MATCH plane="+(zAxis?"Z":"X")+" rotationAxis="+(zAxis?"Z":"X")+" halfWidth="+distance+" length="+length+" backrestCandidates="+backrestCandidates+" blocks="+blocks.size());
    return new Result(new SwingStructure(blocks),pivot,zAxis);
   }
  }
  trace.failed(world,pivot);
  return null;
 }
 private static boolean isChain(BlockState s){return SwingBlockRoles.rope(s);}
 private static boolean isSolid(ServerWorld w, BlockPos p){return SwingBlockRoles.seat(w.getBlockState(p))&&w.getBlockEntity(p)==null;}
 private static BlockPos offset(BlockPos p,int axis,int y,boolean z){return z?p.add(0,y,axis):p.add(axis,y,0);}
 private static BlockPos local(int axis,int y,boolean z){return z?new BlockPos(0,y,axis):new BlockPos(axis,y,0);}
 private static BlockPos localBack(int axis,int y,boolean z,int perp){return z?new BlockPos(perp,y,axis):new BlockPos(axis,y,perp);}
 public record Result(SwingStructure structure, BlockPos pivot, boolean zAxis) {}
}
