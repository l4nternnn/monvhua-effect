package com.kuilunfuzhe.monvhua.features.forensics;

import com.mojang.serialization.Codec;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.world.*;
import java.util.*;

/** Private placement metadata only. Evidence continues to use the viewer-filtered summary API. */
public final class EvidenceAnchorStore extends PersistentState {
    public static final int LIMIT=20;
    public record Anchor(UUID id,UUID owner,String dimension,double x,double y,double z,float yaw,float pitch,float roll,float width) {
        public Anchor {
            Objects.requireNonNull(id);Objects.requireNonNull(owner);
            if(Identifier.tryParse(dimension)==null||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)
                    ||Math.abs(x)>30_000_000||Math.abs(z)>30_000_000||Math.abs(y)>30_000_000
                    ||!Float.isFinite(yaw)||!Float.isFinite(pitch)||!Float.isFinite(roll)||!Float.isFinite(width)||width<.5f||width>8)
                throw new IllegalArgumentException("无效的线索面板位置、旋转或尺寸");
            yaw%=360;pitch%=360;roll%=360;
        }
        public NbtCompound nbt(){var n=new NbtCompound();n.putString("id",id.toString());n.putString("owner",owner.toString());n.putString("dimension",dimension);n.putDouble("x",x);n.putDouble("y",y);n.putDouble("z",z);n.putFloat("yaw",yaw);n.putFloat("pitch",pitch);n.putFloat("roll",roll);n.putFloat("width",width);return n;}
        public static Anchor read(NbtCompound n){return new Anchor(UUID.fromString(n.getString("id","")),UUID.fromString(n.getString("owner","")),n.getString("dimension",""),n.getDouble("x",Double.NaN),n.getDouble("y",Double.NaN),n.getDouble("z",Double.NaN),n.getFloat("yaw",Float.NaN),n.getFloat("pitch",Float.NaN),n.getFloat("roll",Float.NaN),n.getFloat("width",1.5f));}
    }
    public static final Codec<EvidenceAnchorStore> CODEC=NbtCompound.CODEC.xmap(EvidenceAnchorStore::new,EvidenceAnchorStore::nbt);
    public static final PersistentStateType<EvidenceAnchorStore> TYPE=new PersistentStateType<>("monvhua_evidence_anchors",EvidenceAnchorStore::new,CODEC,DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
    private final Map<UUID,Anchor> anchors=new LinkedHashMap<>();
    private long revision;
    public EvidenceAnchorStore(){}
    private EvidenceAnchorStore(NbtCompound n){
        if(n.getInt("schema",1)!=1)throw new IllegalStateException("Unsupported evidence anchor schema");
        revision=Math.max(0,n.getLong("revision",0));
        for(var item:n.getListOrEmpty("anchors"))if(item instanceof NbtCompound a)try{var v=Anchor.read(a);if(owned(v.owner()).size()<LIMIT)anchors.putIfAbsent(v.id(),v);}catch(IllegalArgumentException ignored){}
    }
    private NbtCompound nbt(){var n=new NbtCompound();n.putInt("schema",1);n.putLong("revision",revision);var list=new NbtList();anchors.values().forEach(a->list.add(a.nbt()));n.put("anchors",list);return n;}
    public static EvidenceAnchorStore get(MinecraftServer server){return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE);}
    public List<Anchor> owned(UUID owner){return anchors.values().stream().filter(a->a.owner().equals(owner)).toList();}
    public long revision(){return revision;}
    public void put(Anchor a){var old=anchors.get(a.id());if(old!=null&&!old.owner().equals(a.owner()))throw new IllegalArgumentException("面板不属于当前玩家");if(old==null&&owned(a.owner()).size()>=LIMIT)throw new IllegalArgumentException("世界线索面板已达到上限 "+LIMIT);anchors.put(a.id(),a);changed();}
    public Anchor require(UUID owner,UUID id){var a=anchors.get(id);if(a==null||!a.owner().equals(owner))throw new IllegalArgumentException("面板不存在或不属于当前玩家");return a;}
    public void remove(UUID owner,UUID id){require(owner,id);anchors.remove(id);changed();}
    public void clear(UUID owner){if(anchors.values().removeIf(a->a.owner().equals(owner)))changed();}
    public void clearAll(){if(!anchors.isEmpty()){anchors.clear();changed();}}
    private void changed(){revision++;markDirty();}
}
