package com.kuilunfuzhe.monvhua.features.daynight;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import java.io.IOException;
import java.nio.file.*;

/** World-local storage; no global client config can change a different world. */
public final class DayNightState {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public DayNightConfig config = DayNightConfig.defaults();
    public long revision;
    public DayNightController clock = new DayNightController();

    private static Path path(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("data/monvhua_daynight.json");
    }
    public static DayNightState load(MinecraftServer server) throws IOException {
        Path file = path(server);
        if (!Files.exists(file)) return new DayNightState();
        try {
            DayNightState state = GSON.fromJson(Files.readString(file), DayNightState.class);
            if (state == null || state.config == null || !state.config.valid() || state.revision < 0)
                throw new IllegalArgumentException("Invalid day/night state");
            if (state.clock == null) state.clock = new DayNightController();
            return state;
        } catch (RuntimeException e) { throw new IOException("Invalid day/night state", e); }
    }
    public void save(MinecraftServer server) throws IOException {
        Path file = path(server);
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, GSON.toJson(this));
        try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException e) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
    }
}
