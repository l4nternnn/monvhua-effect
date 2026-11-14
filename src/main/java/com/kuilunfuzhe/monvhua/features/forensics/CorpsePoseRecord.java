package com.kuilunfuzhe.monvhua.features.forensics;

import com.kuilunfuzhe.monvhua.features.physics_preview.PhysicsPoseDocument;
import com.kuilunfuzhe.monvhua.features.physics_preview.StandardBodyPhysicsAdapter;
import net.minecraft.nbt.*;
import net.minecraft.item.ItemStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import org.joml.Matrix4f;
import java.util.*;

/** Versioned public geometry only. No world, renderer or native dependency. */
public record CorpsePoseRecord(long revision, String source, String armModel,
                              Map<String, PhysicsPoseDocument.Pose> parts, String definition, float scale) {
    public CorpsePoseRecord(long revision,String source,String armModel,Map<String,PhysicsPoseDocument.Pose> parts,String definition) {
        this(revision,source,armModel,parts,definition,1);
    }
    public static final String KEY="corpse_pose";
    public CorpsePoseRecord(long revision,String source,String armModel,Map<String,PhysicsPoseDocument.Pose> parts) {
        this(revision,source,armModel,parts,"standard_six_v1");
    }
    public CorpsePoseRecord {
        com.kuilunfuzhe.monvhua.features.physics_preview.CorpseScale.require(scale);
        parts=Map.copyOf(parts);
        if(!Set.of("standard_six_v1","slim_six_v1").contains(definition)
                ||definition.equals("slim_six_v1")&&!armModel.equals("slim"))throw new IllegalArgumentException("Unsupported definition/body shape");
        if(revision<1 || !Set.of("imported_debug","generated","frozen_static","separated").contains(source)
                || !Set.of("slim","default").contains(armModel)
                || parts.isEmpty()) throw new IllegalArgumentException("Invalid pose header/parts");
        PhysicsPoseDocument.validateParts(parts.keySet());
        for(var p:parts.values()) {
            if(!Float.isFinite(p.x())||!Float.isFinite(p.y())||!Float.isFinite(p.z())
                    ||Math.abs(p.x())>8||Math.abs(p.y())>8||Math.abs(p.z())>8
                    ||!p.rotation().isFinite()||Math.abs(p.rotation().lengthSquared()-1)>1e-3f)
                throw new IllegalArgumentException("Invalid pose transform");
        }
        for(String id:parts.keySet()) {
            if(id.equals("torso")||!parts.containsKey("torso"))continue;
            boolean left=id.startsWith("left");
            org.joml.Vector3f parent=id.equals("head")?new org.joml.Vector3f(0,.375f,0):
                    id.endsWith("arm")?new org.joml.Vector3f(left?.25f:-.25f,.375f,0):new org.joml.Vector3f(left?.125f:-.125f,-.375f,0);
            float shoulder=definition.equals("slim_six_v1")?1.5f/16:.125f;
            org.joml.Vector3f child=id.equals("head")?new org.joml.Vector3f(0,-.25f,0):
                    id.endsWith("arm")?new org.joml.Vector3f(left?-shoulder:shoulder,.375f,0):new org.joml.Vector3f(0,.375f,0);
            if(anchor(parts.get("torso"),parent.mul(scale)).distance(anchor(parts.get(id),child.mul(scale)))>.03f*scale)
                throw new IllegalArgumentException("Disconnected joint: "+id);
        }
    }
    private static org.joml.Vector3f anchor(PhysicsPoseDocument.Pose p,org.joml.Vector3f local){return p.rotation().transform(local).add(p.x(),p.y(),p.z());}
    public static boolean present(ItemStack stack) {return data(stack).contains(KEY);}
    public static NbtCompound data(ItemStack stack) {return stack.getOrDefault(DataComponentTypes.CUSTOM_DATA,NbtComponent.DEFAULT).copyNbt();}
    public NbtCompound encode() {
        NbtCompound n=new NbtCompound();n.putInt("version",parts.size()!=6||source.equals("separated")?5:scale!=1?4:source.equals("frozen_static")?3:definition.equals("standard_six_v1")?1:2);n.putString("definition",definition);
        n.putString("coordinate_space","corpse_local");n.putString("state",(source.equals("frozen_static")||source.equals("separated"))?"STATIC":"SETTLED");
        if(scale!=1||parts.size()!=6||source.equals("separated"))n.putFloat("scale",scale);
        n.putString("source",source);n.putLong("revision",revision);n.putString("arm_model",armModel);
        NbtList list=new NbtList();
        for(String id:PhysicsPoseDocument.IDS) {
            if(!parts.containsKey(id))continue;
            var p=parts.get(id);NbtCompound t=new NbtCompound();t.putString("id",id);
            t.putFloat("x",p.x());t.putFloat("y",p.y());t.putFloat("z",p.z());
            t.putFloat("qx",p.qx());t.putFloat("qy",p.qy());t.putFloat("qz",p.qz());t.putFloat("qw",p.qw());list.add(t);
        }
        n.put("parts",list);return n;
    }
    public static Optional<CorpsePoseRecord> read(NbtCompound root) {
        if(root==null||!root.contains(KEY))return Optional.empty();
        try{return Optional.of(decode(root.getCompoundOrEmpty(KEY)));}catch(IllegalArgumentException e){return Optional.empty();}
    }
    public static CorpsePoseRecord decode(NbtCompound n) {
        boolean frozen=Set.of("frozen_static","separated").contains(n.getString("source",""));
        boolean subset=n.getInt("version",0)==5;
        boolean scaled=subset||n.getInt("version",0)==4;
        boolean version=scaled|| (frozen?n.getInt("version",0)==3:
                (n.getInt("version",0)==1&&n.getString("definition","").equals("standard_six_v1"))
                ||(n.getInt("version",0)==2&&n.getString("definition","").equals("slim_six_v1")));
        if(!version||n.getString("source","").equals("separated")&&!subset||!n.getString("coordinate_space","").equals("corpse_local")
                ||!n.getString("state","").equals(frozen?"STATIC":"SETTLED"))
            throw new IllegalArgumentException("Unsupported corpse pose");
        var list=n.getListOrEmpty("parts");if(list.isEmpty()||list.size()>6||!subset&&list.size()!=6)throw new IllegalArgumentException("Invalid part count");
        Map<String,PhysicsPoseDocument.Pose> parts=new LinkedHashMap<>();
        for(var e:list) {
            if(!(e instanceof NbtCompound t))throw new IllegalArgumentException("Invalid part");
            var p=new PhysicsPoseDocument.Pose(f(t,"x"),f(t,"y"),f(t,"z"),f(t,"qx"),f(t,"qy"),f(t,"qz"),f(t,"qw"));
            if(parts.put(t.getString("id",""),p)!=null)throw new IllegalArgumentException("Duplicate part");
        }
        return new CorpsePoseRecord(n.getLong("revision",0),n.getString("source",""),n.getString("arm_model",""),parts,n.getString("definition",""),scaled?f(n,"scale"):1);
    }
    private static float f(NbtCompound n,String key){return n.getFloat(key,Float.NaN);}
    public static CorpsePoseRecord imported(PhysicsPoseDocument document,long revision,String armModel) {
        if(!document.outcome().equals("SETTLED"))throw new IllegalArgumentException("Only SETTLED can be applied");
        return new CorpsePoseRecord(revision,"imported_debug",armModel,document.poses(),document.definition(),document.scale());
    }
    /** Inverse of ItemDisplay NONE + BodyPartSpecialModelRenderer's base root.
     * Existing display placement stays outside this matrix. */
    public static Matrix4f itemCompensation() {return new Matrix4f().scale(-1,-1,1).translate(0,.5f,0);}
    public String rootPart(){return parts.containsKey("torso")?"torso":parts.keySet().iterator().next();}
    public Map<String,Matrix4f> modelMatrices() {
        Map<String,Matrix4f> m=new LinkedHashMap<>();
        for(String id:parts.keySet())m.put(id,itemCompensation().mul(StandardBodyPhysicsAdapter.matrix(id,parts.get(id),armModel.equals("slim"),scale)));
        return m;
    }
}
