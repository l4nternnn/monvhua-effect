package com.kuilunfuzhe.monvhua.features.forensics.cleaning;

import com.mojang.serialization.Codec;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.*;

/** Server-only bounded receipt journal, persisted across normal restarts. */
public final class CleaningActionStore extends PersistentState {
    public static final Codec<CleaningActionStore> CODEC=NbtCompound.CODEC.xmap(CleaningActionStore::new,s->s.root.copy());
    public static final PersistentStateType<CleaningActionStore> TYPE=new PersistentStateType<>("monvhua_cleaning_actions",CleaningActionStore::new,CODEC,DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
    private final NbtCompound root;
    public CleaningActionStore(){this(new NbtCompound());}
    private CleaningActionStore(NbtCompound n){root=n;}
    public static CleaningActionStore get(MinecraftServer s){return s.getOverworld().getPersistentStateManager().getOrCreate(TYPE);}
    public NbtCompound get(String id){return root.getCompoundOrEmpty(id);}
    public int size(){return root.getKeys().size();}
    public void prune(long now){for(String key:java.util.Set.copyOf(root.getKeys()))if(root.getCompoundOrEmpty(key).getLong("until",0)<=now){root.remove(key);markDirty();}}
    public void put(String id,String fingerprint,long now,int changed,int visible,int transferred){put(id,fingerprint,now,changed,visible,transferred,0);}
    public void put(String id,String fingerprint,long now,int changed,int visible,int transferred,int skipped){
        var n=new NbtCompound();n.putString("fingerprint",fingerprint);n.putLong("until",now+72000);
        n.putInt("skipped",skipped);n.putInt("changed",changed);n.putInt("visible",visible);n.putInt("transferred",transferred);root.put(id,n);markDirty();
    }
}
