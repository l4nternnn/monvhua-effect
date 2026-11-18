package com.kuilunfuzhe.monvhua.features.forensics.cards;

import com.kuilunfuzhe.monvhua.features.forensics.*;
import com.kuilunfuzhe.monvhua.features.forensics.blood.*;
import com.kuilunfuzhe.monvhua.mixin.forensics.CorpseDisplayAccessor;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.*;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.nbt.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;

/** Public appearance only: never serializes the corpse ledger or source/event identities. */
public final class ClueSceneEvidence {
    private ClueSceneEvidence(){}
    private static boolean visible(ServerPlayerEntity p,Vec3d eye,Vec3d point){
        var hit=p.getWorld().raycast(new RaycastContext(eye,point,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,p));
        return hit.getType()==HitResult.Type.MISS||hit.getPos().squaredDistanceTo(point)<.0025;
    }
    public static void capture(ServerPlayerEntity p,Vec3d center,Vec3d eye,NbtCompound scene){
        var world=(ServerWorld)p.getWorld();var blood=new NbtList();var bodies=new NbtList();
        var base=BlockPos.ofFloored(center);var seen=new java.util.HashSet<String>();
        for(var pos:BlockPos.iterate(base.add(-2,-2,-2),base.add(2,2,2))) {
            if(!world.isChunkLoaded(pos))continue;
            for(var entry:BloodTraceStore.get(world).at(pos)){
                var patch=entry.patch();if(blood.size()>=128||!seen.add(patch.id())||patch.center().squaredDistanceTo(center)>2.25||patch.mass()<=0)continue;
                var point=patch.center().add(patch.face().getOffsetX()*.01,patch.face().getOffsetY()*.01,patch.face().getOffsetZ()*.01);
                if(!visible(p,eye,point)||eye.subtract(point).dotProduct(new Vec3d(patch.face().getOffsetX(),patch.face().getOffsetY(),patch.face().getOffsetZ()))<=0)continue;
                var n=patch.write();n.remove("id");n.remove("revision");
                n.putDouble("x",pos.getX()-center.x);n.putDouble("y",pos.getY()-center.y);n.putDouble("z",pos.getZ()-center.z);n.remove("pos");blood.add(n);
            }
        }
        for(var display:world.getEntitiesByClass(ItemDisplayEntity.class,new Box(center,center).expand(3),d->Forensics.hasBinding(d.getItemStack()))){
            if(bodies.size()>=8)break;
            var surfaces=CorpsePoseGeometry.surfaces(display,CorpsePoseRecord.data(display.getItemStack()));
            boolean inView=surfaces.stream().anyMatch(s->{var point=CorpsePoseGeometry.bounds(s).offset(display.getPos()).getCenter();return point.squaredDistanceTo(center)<=2.25&&visible(p,eye,point);});
            if(!inView)continue;
            var source=CorpsePoseRecord.data(display.getItemStack());var observation=new NbtCompound();
            var visual=EvidenceSnapshots.visualOnly(source);
            var id=Forensics.binding(display.getItemStack()).getString("id","");
            var life=ForensicStore.get(p.getServer()).corpse(id).getString("id","");
            var stains=new NbtCompound();var transfer=BloodTransferStore.get(p.getServer());
            stains.putInt("hands",BloodTransferService.enabled()?transfer.visible(life+"/hands"):0);
            stains.putInt("torso",BloodTransferService.enabled()?transfer.visible(life+"/torso"):0);
            visual.put("recorded_stains",stains);observation.put("visual",visual);
            var profile=display.getItemStack().get(DataComponentTypes.PROFILE);
            if(profile!=null)ProfileComponent.CODEC.encodeStart(NbtOps.INSTANCE,profile).result().ifPresent(n->observation.put("profile",n));
            var root=new Matrix4f().translation((float)(display.getX()-center.x),(float)(display.getY()-center.y),(float)(display.getZ()-center.z))
                .rotateY((float)Math.toRadians(-display.getYaw())).rotateX((float)Math.toRadians(display.getPitch()))
                .mul(CorpseDisplayAccessor.monvhua$transform(display.getDataTracker()).getMatrix())
                .rotateY((float)Math.PI).translate(-.5f,-.5f,-.5f).translate(.5f,0,.5f).rotateY((float)Math.PI).scale(-1,-1,1);
            var matrix=new NbtList();for(float value:root.get(new float[16]))matrix.add(NbtFloat.of(value));observation.put("root",matrix);
            var body=new NbtCompound();body.put("observation",observation);body.put("wounds",source.getListOrEmpty("corpse_wounds").copy());bodies.add(body);
        }
        scene.put("blood",blood);scene.put("bodies",bodies);scene.putBoolean("has_entities",!bodies.isEmpty());
    }
}
