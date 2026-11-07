package com.kuilunfuzhe.monvhua.features.forensics;

import com.kuilunfuzhe.monvhua.mixin.forensics.CorpseDisplayAccessor;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.nbt.*;
import net.minecraft.server.network.ServerPlayerEntity;
import org.joml.Matrix4f;
import java.util.*;

/** Viewer-owned observation snapshots. Never serialize a forensic truth document or a corpse item. */
public final class EvidenceSnapshots {
    private EvidenceSnapshots() {}
    public static void observe(ServerPlayerEntity viewer, ItemDisplayEntity display, String inspectedPart) {
        String id = Forensics.binding(display.getItemStack()).getString("id", "");
        if (id.isEmpty()) return;
        var store = ForensicStore.get(viewer.getServer());
        NbtCompound snapshot = new NbtCompound();
        snapshot.putString("name", store.corpse(id).getString("name", "未知身份"));
        snapshot.putLong("revision", viewer.getWorld().getTime());
        NbtCompound source = display.getItemStack().getOrDefault(DataComponentTypes.CUSTOM_DATA,NbtComponent.DEFAULT).copyNbt();
        NbtCompound visual=visualOnly(source);
        NbtList paint=new NbtList();
        var oldVisual=store.observations(viewer.getUuid()).getCompoundOrEmpty(id).getCompoundOrEmpty("visual");
        for(var element:oldVisual.getListOrEmpty("model_paint_faces"))
            if(element instanceof NbtCompound face&&!paintPart(face.getString("surface","")).equals(inspectedPart))paint.add(face.copy());
        for(var face:com.kuilunfuzhe.monvhua.features.paint.ModelPaintData.readFaces(source,source.getString("arm_model","").equals("slim"))) {
            if(!paintPart(face.surface()).equals(inspectedPart)||paint.size()>=192)continue;
            NbtCompound entry=new NbtCompound();entry.putString("surface",face.surface());entry.putInt("face",face.face().getIndex());entry.putIntArray("pixels",face.pixels());paint.add(entry);
        }
        visual.put("model_paint_faces",paint);snapshot.put("visual",visual);
        var profile = display.getItemStack().get(DataComponentTypes.PROFILE);
        if (profile != null) ProfileComponent.CODEC.encodeStart(NbtOps.INSTANCE,profile).result().ifPresent(n -> snapshot.put("profile",n));
        Matrix4f root = new Matrix4f().rotateY((float)Math.toRadians(-display.getYaw()))
                .rotateX((float)Math.toRadians(display.getPitch()))
                .mul(CorpseDisplayAccessor.monvhua$transform(display.getDataTracker()).getMatrix())
                .rotateY((float)Math.PI).translate(-.5f,-.5f,-.5f)
                .translate(.5f,0,.5f).rotateY((float)Math.PI).scale(-1,-1,1);
        NbtList matrix = new NbtList(); for (float f : root.get(new float[16])) matrix.add(NbtFloat.of(f));
        snapshot.put("root",matrix);
        snapshot.putBoolean("partial", !Forensics.binding(display.getItemStack()).getString("part","all").equals("all"));
        snapshot.put("environment",EvidenceBlockSnapshot.capture((net.minecraft.server.world.ServerWorld)display.getWorld(),display.getPos()));
        store.observe(viewer.getUuid(),id,snapshot);
    }

    private static String paintPart(String surface) {
        if(surface.startsWith("body_"))return "torso";
        for(String part:Forensics.PARTS)if(surface.startsWith(part))return part;
        return "unknown";
    }

    public static NbtCompound visualOnly(NbtCompound source) {
        NbtCompound result = new NbtCompound();
        for (String key : List.of("local_skin","arm_model","corpse_scale","body_pose_mode"))
            if (source.contains(key)) result.put(key,source.get(key).copy());
        for (String part : List.of("head","torso","left_arm","right_arm","left_leg","right_leg","model"))
            for (String field : List.of("pitch","yaw","roll","scale","offset_x","offset_y","offset_z"))
                for (String prefix : List.of("pose_","bend_")) {
                    String key=prefix+part+"_"+field;
                    if (source.contains(key)) {
                        float value=source.getFloat(key,0);
                        if(Float.isFinite(value))result.putFloat(key,field.equals("scale")?Math.max(.1f,Math.min(64,value)):Math.max(-10000,Math.min(10000,value)));
                    }
                }
        float scale=source.getFloat("corpse_scale",1);
        result.putFloat("corpse_scale",Float.isFinite(scale)?Math.max(.01f,Math.min(64,scale)):1);
        return result;
    }

    public static NbtCompound publicWound(NbtCompound hit) {
        NbtCompound result = new NbtCompound();
        for (String key : List.of("id","part","kinds","severity","direction","precision"))
            if (hit.contains(key)) result.putString(key,hit.getString(key,""));
        if (!result.contains("severity")) {
            float amount=hit.getFloat("amount",0);
            result.putString("severity",amount<ForensicConfig.current.moderateThreshold?"mild":amount<ForensicConfig.current.severeThreshold?"moderate":"severe");
        }
        if (hit.getBoolean("death_trigger",false)) result.putBoolean("death_trigger",true);
        NbtCompound surface=hit.getCompoundOrEmpty("wound_surface");
        for(String key:List.of("surface","face")) if(surface.contains(key)) result.putString(key,surface.getString(key,""));
        for(String key:List.of("u","v")) if(surface.contains(key)) result.putDouble(key,surface.getDouble(key,0));
        return result;
    }

    public static NbtCompound summary(ServerPlayerEntity viewer, String requested) {
        return summary(ForensicStore.get(viewer.getServer()),viewer.getUuid(),requested);
    }
    public static NbtCompound summary(ForensicStore store, UUID viewer, String requested) {
        NbtCompound observations=store.observations(viewer), result=new NbtCompound();
        for(String id:store.collectedCaseIds(viewer))if(!observations.contains(id)){
            NbtCompound legacy=new NbtCompound();legacy.putString("name",store.corpse(id).getString("name","未知身份"));legacy.putBoolean("missing",true);observations.put(id,legacy);
        }
        var ids=new ArrayList<>(observations.getKeys());
        ids.sort(Comparator.<String>comparingLong(id -> observations.getCompoundOrEmpty(id).getLong("revision",0)).reversed().thenComparing(id->id));
        NbtList cases=new NbtList();
        for(String id:ids.stream().limit(128).toList()) {
            NbtCompound entry=new NbtCompound();entry.putString("id",id);
            entry.putString("name",observations.getCompoundOrEmpty(id).getString("name","未知身份"));cases.add(entry);
        }
        result.put("cases",cases);
        String selected=requested.isEmpty()?(ids.isEmpty()?"":ids.getFirst()):requested;
        if(!ids.contains(selected)) return result;
        result.putString("id",selected);result.put("observation",observations.getCompoundOrEmpty(selected).copy());
        NbtList wounds=new NbtList();
        for(String part:Forensics.PARTS) for(var hit:store.collected(viewer,selected,part).stream().limit(3).toList()) wounds.add(publicWound(hit));
        result.put("wounds",wounds);
        return result;
    }
}
