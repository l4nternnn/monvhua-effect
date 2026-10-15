package com.kuilunfuzhe.monvhua.features.daynight;

import com.google.gson.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import java.io.IOException;
import java.nio.file.*;

public final class DayNightState {
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    public int schemaVersion=3;
    public DayNightConfig config=DayNightConfig.defaults();
    public DayNightConfig savedConfig=config;
    public long revision;
    public DayNightController clock=new DayNightController();
    private static Path path(MinecraftServer server) { return server.getSavePath(WorldSavePath.ROOT).resolve("data/monvhua_daynight.json"); }
    public DayNightState copy() {
        DayNightState next=new DayNightState();next.config=config;next.savedConfig=savedConfig;
        next.revision=revision;next.clock=clock;return next;
    }
    public static DayNightState load(MinecraftServer server) throws IOException {
        Path file=path(server);if(!Files.exists(file)) return new DayNightState();
        try {
            JsonObject obj=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            int version=obj.has("schemaVersion")?obj.get("schemaVersion").getAsInt():2;
            if(version>3) throw new IllegalArgumentException("Unsupported future schema");
            DayNightState state=new DayNightState();
            state.config=DayNightConfigMigration.read(obj.get("config"));
            state.savedConfig=obj.has("savedConfig")?DayNightConfigMigration.read(obj.get("savedConfig")):state.config;
            state.revision=obj.has("revision")?obj.get("revision").getAsLong():0;
            if(state.revision<0) throw new IllegalArgumentException("Invalid revision");
            if(version==3&&obj.has("clock")) state.clock=GSON.fromJson(obj.get("clock"),DayNightController.class);
            if(state.clock==null) state.clock=new DayNightController();
            if(version<3) {
                Path backup=file.resolveSibling("monvhua_daynight.pre-v3.json");
                if(!Files.exists(backup)) Files.copy(file,backup);
            }
            return state;
        } catch(RuntimeException e) { throw new IOException("Invalid day/night state",e); }
    }
    public void save(MinecraftServer server) throws IOException {
        Path file=path(server);Files.createDirectories(file.getParent());
        Path temp=file.resolveSibling(file.getFileName()+".tmp");Files.writeString(temp,GSON.toJson(this));
        try { Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
        catch(AtomicMoveNotSupportedException e) { Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING); }
    }
}
