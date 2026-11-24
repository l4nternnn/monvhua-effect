package com.kuilunfuzhe.monvhua.features.forensics;
import com.kuilunfuzhe.monvhua.features.block.body.BodyPartManager;
import com.kuilunfuzhe.monvhua.mixin.forensics.CorpseDisplayAccessor;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.entity.decoration.InteractionEntity;
import net.minecraft.component.*;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.*;
public final class CorpseGeometrySync {
 private record Cached(NbtComponent data,Matrix4f transform,float yaw,float pitch,InteractionEntity interaction,Box bounds) {}
 private static final Map<UUID,Cached> cache=new HashMap<>();
 public static void forget(UUID id){cache.remove(id);}
 public static void clear(){cache.clear();}
 public static void update(ItemDisplayEntity display){
  long started=CorpsePerformance.begin();
  try{updateGeometry(display);}finally{CorpsePerformance.end(CorpsePerformance.Metric.GEOMETRY,started);}
 }
 private static void updateGeometry(ItemDisplayEntity display){
  if(!(display.getWorld() instanceof ServerWorld world))return;
  var id=BodyPartManager.DISPLAY_TO_INTERACTION.get(display.getUuid());
  if(id==null||!(world.getEntity(id) instanceof InteractionEntity interaction)){forget(display.getUuid());return;}
  var data=display.getItemStack().getOrDefault(DataComponentTypes.CUSTOM_DATA,NbtComponent.DEFAULT);
  var transform=CorpseDisplayAccessor.monvhua$transform(display.getDataTracker()).getMatrix();
  var previous=cache.get(display.getUuid());
  if(previous!=null&&previous.interaction==interaction&&previous.data.equals(data)&&previous.yaw==display.getYaw()
    &&previous.pitch==display.getPitch()&&previous.transform.equals(transform)){
   CorpsePerformance.count(CorpsePerformance.Counter.GEOMETRY_CACHED);
   apply(display,interaction,previous.bounds);return;
  }
  forget(display.getUuid());
  if(!Forensics.hasBinding(display.getItemStack())||!Forensics.binding(display.getItemStack()).getString("part","").equals("all")&&!CorpsePoseRecord.present(display.getItemStack()))return;
  var n=display.getItemStack().getOrDefault(DataComponentTypes.CUSTOM_DATA,NbtComponent.DEFAULT).copyNbt();
  // Nonstandard skeletal poses need a skeleton adapter, not an invented box.
  if(n.getString("body_pose_mode","").equals("true_skeletal"))return;
  Matrix4f m=placement(display,n);
  Box bounds = bounds(m, n);
  if (bounds == null) return;
  cache.put(display.getUuid(),new Cached(data,new Matrix4f(transform),display.getYaw(),display.getPitch(),interaction,bounds));
  CorpsePerformance.count(CorpsePerformance.Counter.GEOMETRY_REBUILT);
  // A replacement interaction needs its tracked local bounds even if its default box happens to match.
  interaction.setPosition(display.getPos());((CorpseBounds)interaction).monvhua$bounds(bounds);
 }
 private static void apply(ItemDisplayEntity display,InteractionEntity interaction,Box bounds){
  if(!interaction.getPos().equals(display.getPos()))interaction.setPosition(display.getPos());
  var target=bounds.offset(display.getPos());
  if(!interaction.getBoundingBox().equals(target))((CorpseBounds)interaction).monvhua$bounds(bounds);
 }

 public static Matrix4f placement(ItemDisplayEntity display,net.minecraft.nbt.NbtCompound n) {
  return new Matrix4f().rotateY((float)Math.toRadians(-display.getYaw())).rotateX((float)Math.toRadians(display.getPitch()))
   .mul(CorpseDisplayAccessor.monvhua$transform(display.getDataTracker()).getMatrix()).rotateY((float)Math.PI).translate(-.5f,-.5f,-.5f)
   .translate(.5f,0,.5f).rotateY((float)Math.PI).scale(-1,-1,1)
   .translate(n.getFloat("pose_model_offset_x",0),n.getFloat("pose_model_offset_y",0),n.getFloat("pose_model_offset_z",0))
   .rotateX((float)Math.toRadians(n.getFloat("pose_model_pitch",0))).rotateY((float)Math.toRadians(-n.getFloat("pose_model_yaw",0)))
   .rotateZ((float)Math.toRadians(n.getFloat("pose_model_roll",0))).scale(CorpsePoseRecord.read(n).isPresent()?1:Math.max(.1f,n.getFloat("pose_model_scale",1))*n.getFloat("corpse_scale",1));
 }

 /** Union of posed surfaces, in display-relative coordinates. Null rejects malformed transforms. */
 public static Box bounds(Matrix4f placement, net.minecraft.nbt.NbtCompound data) {
  float minX=Float.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=Float.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
  for (var surface : WoundGeometry.surfaces(data)) {
   var b=surface.box();
   Matrix4f posed=new Matrix4f(placement).mul(CorpseSurfacePose.transform(surface,data));
   for(int i=0;i<8;i++) {
    Vector3f p=posed.transformPosition(new Vector3f((float)((i&1)==0?b.minX:b.maxX)/16,
      (float)((i&2)==0?b.minY:b.maxY)/16,(float)((i&4)==0?b.minZ:b.maxZ)/16));
    if (!p.isFinite()) return null;
    minX=Math.min(minX,p.x); minY=Math.min(minY,p.y); minZ=Math.min(minZ,p.z);
    maxX=Math.max(maxX,p.x); maxY=Math.max(maxY,p.y); maxZ=Math.max(maxZ,p.z);
   }
  }
  return new Box(minX,minY,minZ,maxX,maxY,maxZ).expand(.04);
 }
}
