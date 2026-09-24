package com.kuilunfuzhe.monvhua.gui.commandpanel;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kuilunfuzhe.monvhua.network.commandpanel.CommandPanelPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CommandPanelSyncManager {
    private static final int CHUNK_LENGTH = 8000;
    private static final int MAX_CHUNKS = 256;
    private static final int MAX_CONFIG_LENGTH = CHUNK_LENGTH * MAX_CHUNKS;
    private static final Map<String, IncomingTransfer> INCOMING = new HashMap<>();
    private static Pending pending;

    private CommandPanelSyncManager() {}

    public static void receive(String source, long revision, String json) {
        pending = new Pending(source, revision, json);
    }

    public static void receiveChunk(String source, long revision, String transferId, int index, int count, String chunk) {
        if (transferId == null || transferId.length() > 40 || count < 1 || count > MAX_CHUNKS || index < 0 || index >= count
                || chunk == null || chunk.length() > CHUNK_LENGTH) return;
        if (!INCOMING.containsKey(transferId) && INCOMING.size() >= 8) INCOMING.clear();
        IncomingTransfer transfer = INCOMING.computeIfAbsent(transferId,
                ignored -> new IncomingTransfer(source, revision, count));
        if (!transfer.matches(source, revision, count)) {
            INCOMING.remove(transferId);
            return;
        }
        if (!transfer.add(index, chunk)) {
            INCOMING.remove(transferId);
            return;
        }
        if (transfer.complete()) {
            INCOMING.remove(transferId);
            String json = transfer.join();
            if (json != null && json.length() <= MAX_CONFIG_LENGTH) receive(source, revision, json);
        }
    }

    public static Pending pending() { return pending; }
    public static void clear() { pending = null; }

    public static void uploadLocal() {
        MinecraftClient client = MinecraftClient.getInstance();
        try {
            Path file = file();
            String json = Files.exists(file) ? Files.readString(file) : "[]";
            if (json.length() > MAX_CONFIG_LENGTH) {
                notifyPlayer("命令面板配置超过同步上限（" + MAX_CONFIG_LENGTH + " 字符）。");
                return;
            }
            if (ClientPlayNetworking.canSend(CommandPanelPackets.SyncUploadChunkC2S.ID)) {
                sendChunks(json);
            } else if (json.length() <= 32767 && ClientPlayNetworking.canSend(CommandPanelPackets.SyncUploadC2S.ID)) {
                ClientPlayNetworking.send(new CommandPanelPackets.SyncUploadC2S(json));
            } else {
                notifyPlayer("当前服务器不支持大配置分块同步；请更新服务器端模组，或将配置缩小到 32767 字符以内。");
            }
        } catch (Exception exception) {
            notifyPlayer("命令面板配置同步读取失败：" + exception.getMessage());
        }
    }

    private static void sendChunks(String json) {
        int count = Math.max(1, (json.length() + CHUNK_LENGTH - 1) / CHUNK_LENGTH);
        if (count > MAX_CHUNKS) {
            notifyPlayer("命令面板配置分块数量超过上限。");
            return;
        }
        String transferId = UUID.randomUUID().toString();
        for (int index = 0, offset = 0; index < count; index++) {
            int end = Math.min(json.length(), offset + CHUNK_LENGTH);
            if (end < json.length() && end > offset && Character.isHighSurrogate(json.charAt(end - 1))
                    && Character.isLowSurrogate(json.charAt(end))) end--;
            String chunk = json.substring(offset, end);
            ClientPlayNetworking.send(new CommandPanelPackets.SyncUploadChunkC2S(transferId, index, count, chunk));
            offset = end;
        }
    }

    public static void accept() {
        if (pending == null) return;
        Pending received = pending;
        try {
            String compatible = normalizeForStorage(received.json(), received.revision());
            Files.createDirectories(file().getParent());
            writeAtomically(file(), compatible);
            ClientPlayNetworking.send(new CommandPanelPackets.SyncDecisionC2S(received.revision(), true));
            clear();
            MinecraftClient.getInstance().setScreen(new CommandPanelScreen());
        } catch (Exception exception) {
            notifyPlayer("收到的命令面板配置无效，未覆盖本地文件：" + exception.getMessage());
        }
    }

    public static void reject() {
        if (pending != null) ClientPlayNetworking.send(new CommandPanelPackets.SyncDecisionC2S(pending.revision(), false));
        clear();
        MinecraftClient.getInstance().setScreen(new CommandPanelScreen());
    }

    private static String normalizeForStorage(String json, long incomingRevision) {
        JsonElement root = JsonParser.parseString(json);
        if (root.isJsonArray()) {
            JsonObject panel = new JsonObject();
            String panelId = UUID.randomUUID().toString();
            panel.addProperty("id", panelId);
            panel.addProperty("name", "面板 1");
            panel.add("popups", root.deepCopy());
            JsonArray panels = new JsonArray();
            panels.add(panel);
            JsonObject upgraded = new JsonObject();
            upgraded.addProperty("version", 3);
            upgraded.addProperty("revision", incomingRevision);
            upgraded.addProperty("activePanelId", panelId);
            upgraded.add("popups", root.deepCopy());
            upgraded.add("panels", panels);
            return upgraded.toString();
        }
        if (!root.isJsonObject()) throw new IllegalArgumentException("根节点格式不正确");
        JsonObject document = root.getAsJsonObject().deepCopy();
        long oldRevision = document.has("revision") ? document.get("revision").getAsLong() : 0;
        document.addProperty("revision", Math.max(incomingRevision, oldRevision + 1));
        if (!document.has("panels") || !document.get("panels").isJsonArray() || document.getAsJsonArray("panels").size() == 0) {
            String panelId = UUID.randomUUID().toString();
            JsonArray popups = document.has("popups") && document.get("popups").isJsonArray()
                    ? document.getAsJsonArray("popups").deepCopy() : new JsonArray();
            JsonObject panel = new JsonObject();
            panel.addProperty("id", panelId);
            panel.addProperty("name", "面板 1");
            panel.add("popups", popups.deepCopy());
            JsonArray panels = new JsonArray();
            panels.add(panel);
            document.addProperty("version", 3);
            document.addProperty("activePanelId", panelId);
            document.add("panels", panels);
            document.add("popups", popups);
        } else {
            document.addProperty("version", 3);
            if (!document.has("activePanelId") || document.get("activePanelId").isJsonNull()) {
                JsonObject first = document.getAsJsonArray("panels").get(0).getAsJsonObject();
                document.addProperty("activePanelId", first.get("id").getAsString());
            }
            if (!document.has("popups")) {
                JsonArray fallbackPopups = new JsonArray();
                for (JsonElement panelElement : document.getAsJsonArray("panels")) {
                    JsonObject panel = panelElement.getAsJsonObject();
                    if (panel.has("id") && panel.get("id").isJsonPrimitive()
                            && panel.get("id").getAsString().equals(document.get("activePanelId").getAsString())) {
                        if (panel.has("popups") && panel.get("popups").isJsonArray()) fallbackPopups = panel.getAsJsonArray("popups").deepCopy();
                        break;
                    }
                }
                if (fallbackPopups.size() == 0 && document.getAsJsonArray("panels").get(0).isJsonObject()) {
                    JsonObject first = document.getAsJsonArray("panels").get(0).getAsJsonObject();
                    if (first.has("popups") && first.get("popups").isJsonArray()) fallbackPopups = first.getAsJsonArray("popups").deepCopy();
                }
                document.add("popups", fallbackPopups);
            }
        }
        return document.toString();
    }

    private static void writeAtomically(Path path, String contents) throws Exception {
        Path temporary = Files.createTempFile(path.getParent(), "monvhua_command_panel_sync", ".tmp");
        try {
            Files.writeString(temporary, contents);
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void notifyPlayer(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) client.player.sendMessage(Text.literal(message), false);
    }

    private static Path file() {
        return MinecraftClient.getInstance().runDirectory.toPath().resolve("config/monvhua_command_panel.json");
    }

    public record Pending(String source, long revision, String json) {}

    private static final class IncomingTransfer {
        private final String source;
        private final long revision;
        private final String[] chunks;
        private int received;
        private int length;

        private IncomingTransfer(String source, long revision, int count) {
            this.source = source;
            this.revision = revision;
            this.chunks = new String[count];
        }

        private boolean matches(String otherSource, long otherRevision, int count) {
            return source.equals(otherSource) && revision == otherRevision && chunks.length == count;
        }

        private boolean add(int index, String chunk) {
            if (chunks[index] != null) return chunks[index].equals(chunk);
            length += chunk.length();
            if (length > MAX_CONFIG_LENGTH) return false;
            chunks[index] = chunk;
            received++;
            return true;
        }

        private boolean complete() { return received == chunks.length; }

        private String join() {
            StringBuilder json = new StringBuilder(length);
            for (String chunk : chunks) {
                if (chunk == null) return null;
                json.append(chunk);
            }
            return json.toString();
        }
    }
}
