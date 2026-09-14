package com.kuilunfuzhe.monvhua.features.commandpanel;

import com.mojang.serialization.Codec;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;
import java.util.*;

public final class CommandPanelStore extends PersistentState {
    private String json = "[]";
    public static final Codec<CommandPanelStore> CODEC = Codec.STRING.xmap(s -> { CommandPanelStore v=new CommandPanelStore(); v.json=s; return v; }, v -> v.json);
    public static final PersistentStateType<CommandPanelStore> TYPE = new PersistentStateType<>("monvhua_command_panels", CommandPanelStore::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
    public CommandPanelStore() {}

    public static CommandPanelStore get(ServerWorld world){return world.getPersistentStateManager().getOrCreate(TYPE);}


}
