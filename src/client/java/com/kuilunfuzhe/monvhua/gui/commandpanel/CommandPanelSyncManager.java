package com.kuilunfuzhe.monvhua.gui.commandpanel;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.kuilunfuzhe.monvhua.network.commandpanel.CommandPanelPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class CommandPanelSyncManager {
    private static final Gson GSON = new Gson();
    private static final java.lang.reflect.Type TYPE = new TypeToken<List<CommandPanelScreen.Popup>>(){}.getType();
    private static Pending pending;
    private CommandPanelSyncManager() {}
    public static void receive(String source, long revision, String json) { pending = new Pending(source, revision, json); }
    public static Pending pending() { return pending; }
    public static void clear() { pending = null; }
    public static void uploadLocal() { try { Path f=file(); String json=Files.exists(f)?Files.readString(f):"[]"; ClientPlayNetworking.send(new CommandPanelPackets.SyncUploadC2S(json)); } catch (Exception ignored) {} }
    public static void accept() { if (pending == null) return; Pending p=pending; try { Files.createDirectories(file().getParent()); Files.writeString(file(), p.json); } catch (Exception ignored) {} ClientPlayNetworking.send(new CommandPanelPackets.SyncDecisionC2S(p.revision,true)); clear(); MinecraftClient.getInstance().setScreen(new CommandPanelScreen()); }
    public static void reject() { if (pending != null) ClientPlayNetworking.send(new CommandPanelPackets.SyncDecisionC2S(pending.revision,false)); clear(); MinecraftClient.getInstance().setScreen(new CommandPanelScreen()); }
    private static Path file() { return MinecraftClient.getInstance().runDirectory.toPath().resolve("config/monvhua_command_panel.json"); }
    public record Pending(String source, long revision, String json) {}
}
