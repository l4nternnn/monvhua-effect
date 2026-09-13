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
    public CommandPanelStore() {}
    private CommandPanelStore(Map<UUID,String> values){this.values.putAll(values);}
    public static CommandPanelStore get(ServerWorld world){return world.getPersistentStateManager().getOrCreate(TYPE);}
    public String get(UUID id){return values.getOrDefault(id,"[]");}
    public void put(UUID id,String json){values.put(id,json);markDirty();}
}
