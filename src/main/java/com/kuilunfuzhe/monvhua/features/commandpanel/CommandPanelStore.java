package com.kuilunfuzhe.monvhua.features.commandpanel;

import com.mojang.serialization.Codec;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Uuids;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;
import java.util.*;

public final class CommandPanelStore extends PersistentState {
    public static final Codec<CommandPanelStore> CODEC = Codec.unboundedMap(Uuids.CODEC, Codec.STRING).xmap(CommandPanelStore::new, s -> s.values);
    public static final PersistentStateType<CommandPanelStore> TYPE = new PersistentStateType<>("monvhua_command_panels", CommandPanelStore::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
    private final Map<UUID,String> values = new HashMap<>();
    private final Map<UUID,Long> revisions = new HashMap<>();
    public CommandPanelStore() {}
    private CommandPanelStore(Map<UUID,String> values){this.values.putAll(values);}
    public static CommandPanelStore get(ServerWorld world){return world.getPersistentStateManager().getOrCreate(TYPE);}
    public String get(UUID id){return values.getOrDefault(id,"[]");}
    public long revision(UUID id){return revisions.getOrDefault(id,0L);}
    public boolean putIfRevision(UUID id,long revision,String json){if(revision!=revision(id))return false; values.put(id,json); revisions.put(id,revision+1); markDirty(); return true;}
    public void put(UUID id,String json){values.put(id,json); revisions.put(id,revision(id)+1); markDirty();}
}
