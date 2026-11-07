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
public final class CorpseGeometrySync {
 public static void update(ItemDisplayEntity display){
  if(!(display.getWorld() instanceof ServerWorld world)||!Forensics.hasBinding(display.getItemStack())||!Forensics.binding(display.getItemStack()).getString("part","").equals("all"))return;
  var id=BodyPartManager.DISPLAY_TO_INTERACTION.get(display.getUuid());
  if(id==null||!(world.getEntity(id) instanceof InteractionEntity interaction))return;
  var n=display.getItemStack().getOrDefault(DataComponentTypes.CUSTOM_DATA,NbtComponent.DEFAULT).copyNbt();
  // Nonstandard skeletal poses need a skeleton adapter, not an invented box.
  if(n.getString("body_pose_mode","").equals("true_skeletal"))return;
  Matrix4f m=new Matrix4f().rotateY((float)Math.toRadians(-display.getYaw())).rotateX((float)Math.toRadians(display.getPitch()))
   .mul(CorpseDisplayAccessor.monvhua$transform(display.getDataTracker()).getMatrix()).rotateY((float)Math.PI).translate(-.5f,-.5f,-.5f)
   .translate(.5f,0,.5f).rotateY((float)Math.PI).scale(-1,-1,1)
   .translate(n.getFloat("pose_model_offset_x",0),n.getFloat("pose_model_offset_y",0),n.getFloat("pose_model_offset_z",0))
   .rotateX((float)Math.toRadians(n.getFloat("pose_model_pitch",0))).rotateY((float)Math.toRadians(-n.getFloat("pose_model_yaw",0)))
   .rotateZ((float)Math.toRadians(n.getFloat("pose_model_roll",0))).scale(Math.max(.1f,n.getFloat("pose_model_scale",1))*n.getFloat("corpse_scale",1));
  Box bounds = bounds(m, n);
  if (bounds == null) return;
  interaction.setPosition(display.getPos());
  ((CorpseBounds)interaction).monvhua$bounds(bounds);
 }

 /** Union of posed surfaces, in display-relative coordinates. Null rejects malformed transforms. */
 public static Box bounds(Matrix4f placement, net.minecraft.nbt.NbtCompound data) {
  float minX=Float.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=Float.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
  for (var surface : WoundGeometry.SURFACES) {
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
