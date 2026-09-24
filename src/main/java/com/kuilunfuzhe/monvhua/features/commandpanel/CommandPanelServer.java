package com.kuilunfuzhe.monvhua.features.commandpanel;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kuilunfuzhe.monvhua.network.commandpanel.CommandPanelPackets;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.text.Text;
import java.util.concurrent.ConcurrentHashMap;
import java.util.*;

public final class CommandPanelServer {
    private static final Map<UUID, List<UUID>> PENDING = new HashMap<>();
    private static final Map<UUID, Long> LAST_SHARED_REVISION = new HashMap<>();
    private static final Map<UploadKey, IncomingUpload> UPLOADS = new ConcurrentHashMap<>();
    private static final Gson GSON = new Gson();
    private static final int CHUNK_LENGTH = 8000;
    private static final int MAX_CHUNKS = 256;
    private static final int MAX_CONFIG_LENGTH = CHUNK_LENGTH * MAX_CHUNKS;
    private static final String EDIT_TAG = "ui_edit";
    private CommandPanelServer() {}
    private static boolean canEdit(ServerPlayerEntity player) {
        return player.isCreative() && player.getCommandTags().contains(EDIT_TAG);
    }
    public static void initialize() {
        PanelStatusServer.initialize();
        CommandPanelPackets.ReloadS2C.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
            CommandManager.literal("commandpanel")
                .then(CommandManager.literal("reload")
                .requires(source -> source.hasPermissionLevel(2))
                .executes(ctx -> { ctx.getSource().sendError(Text.translatable("command.monvhua.commandpanel.reload.usage")); return 0; })
                .then(CommandManager.argument("targets", EntityArgumentType.players()).executes(ctx -> {
                    Collection<ServerPlayerEntity> targets = EntityArgumentType.getPlayers(ctx, "targets");
                    int sent = 0;
                    for (ServerPlayerEntity target : targets) {
                        if (ServerPlayNetworking.canSend(target, CommandPanelPackets.ReloadS2C.ID)) {
                            ServerPlayNetworking.send(target, new CommandPanelPackets.ReloadS2C());
                            sent++;
                        }
                    }
                    int count = sent;
                    ctx.getSource().sendFeedback(() -> Text.translatable("command.monvhua.commandpanel.reload.success", count), false);
                    return count;
                })))
                .then(CommandManager.literal("sync")
                .executes(ctx -> { ctx.getSource().sendError(Text.translatable("command.monvhua.commandpanel.sync.usage")); return 0; })
                .then(CommandManager.argument("targets", EntityArgumentType.players()).executes(ctx -> {
                    ServerPlayerEntity sender = ctx.getSource().getPlayerOrThrow();
                    if (!canEdit(sender)) { ctx.getSource().sendError(Text.translatable("command.monvhua.commandpanel.no_permission")); return 0; }
                    ServerWorld world = sender.getServer().getOverworld();
                    List<UUID> targets = EntityArgumentType.getPlayers(ctx, "targets").stream().map(ServerPlayerEntity::getUuid).toList();
                    PENDING.put(sender.getUuid(), targets);
                    ServerPlayNetworking.send(sender, new CommandPanelPackets.SyncRequestS2C(targets.stream().map(UUID::toString).collect(java.util.stream.Collectors.joining(","))));
                    int count = targets.size();
                    int synced = count;
                    ctx.getSource().sendFeedback(() -> Text.translatable("command.monvhua.commandpanel.sync.success", synced), false);
                    return count;
                })))));
        ServerPlayNetworking.registerGlobalReceiver(CommandPanelPackets.RequestC2S.ID, (packet, context) -> context.server().execute(() -> {
            ServerPlayerEntity player = context.player();
            ServerWorld world = player.getServer().getOverworld();
            ServerPlayNetworking.send(player, new CommandPanelPackets.PermissionS2C(canEdit(player)));

        }));
        ServerPlayNetworking.registerGlobalReceiver(CommandPanelPackets.SyncUploadC2S.ID, (packet, context) -> context.server().execute(() -> {
            ServerPlayerEntity sender = context.player();
            if (!canEdit(sender) || packet.json().length() > 32767) return;
            completeUpload(sender, packet.json());
        }));
        ServerPlayNetworking.registerGlobalReceiver(CommandPanelPackets.SyncUploadChunkC2S.ID, (packet, context) -> context.server().execute(() ->
                receiveUploadChunk(context.player(), packet)));
        ServerPlayNetworking.registerGlobalReceiver(CommandPanelPackets.SyncDecisionC2S.ID, (packet, context) -> context.server().execute(() -> {
            ServerPlayerEntity receiver = context.player();
            Long expected = LAST_SHARED_REVISION.get(receiver.getUuid());
            if (expected == null || expected.longValue() != packet.revision()) return;
            LAST_SHARED_REVISION.remove(receiver.getUuid());
            receiver.sendMessage(Text.literal(packet.accept()
                    ? "已接受命令面板同步配置。"
                    : "已保留本地命令面板配置。"), false);
        }));

        ServerPlayNetworking.registerGlobalReceiver(CommandPanelPackets.ExecuteC2S.ID, (packet, context) -> context.server().execute(() -> {
            ServerPlayerEntity player = context.player();
            String command = packet.command();
            if (command == null || command.length() > 32767 || command.indexOf('\n') >= 0) return;
            player.getServer().getCommandManager().executeWithPrefix(player.getCommandSource(), command.startsWith("/") ? command.substring(1) : command);
        }));
    }

    private static void receiveUploadChunk(ServerPlayerEntity sender, CommandPanelPackets.SyncUploadChunkC2S packet) {
        if (!canEdit(sender)) return;
        if (packet.transferId() == null || packet.transferId().length() > 40 || packet.count() < 1 || packet.count() > MAX_CHUNKS
                || packet.index() < 0 || packet.index() >= packet.count() || packet.chunk() == null || packet.chunk().length() > CHUNK_LENGTH) {
            return;
        }
        long now = System.currentTimeMillis();
        UPLOADS.entrySet().removeIf(entry -> now - entry.getValue().lastUpdated > 120_000);
        UploadKey key = new UploadKey(sender.getUuid(), packet.transferId());
        if (!UPLOADS.containsKey(key) && UPLOADS.size() >= 16) {
            sender.sendMessage(Text.literal("当前有过多命令面板同步任务，请稍后重试。"), false);
            return;
        }
        IncomingUpload upload = UPLOADS.computeIfAbsent(key, ignored -> new IncomingUpload(packet.count(), now));
        if (!upload.add(packet.index(), packet.count(), packet.chunk(), now)) {
            UPLOADS.remove(key);
            sender.sendMessage(Text.literal("命令面板同步分块无效或超出大小限制，已取消。"), false);
            return;
        }
        if (upload.complete()) {
            UPLOADS.remove(key);
            String json = upload.join();
            if (json != null) completeUpload(sender, json);
        }
    }

    private static void completeUpload(ServerPlayerEntity sender, String json) {
        if (!canEdit(sender) || json == null || json.length() > MAX_CONFIG_LENGTH) return;
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject() && !root.isJsonArray()) throw new IllegalArgumentException("root must be an object or array");
        } catch (Exception exception) {
            sender.sendMessage(Text.literal("命令面板同步失败：配置 JSON 无效。"), false);
            PENDING.remove(sender.getUuid());
            return;
        }
        List<UUID> targets = PENDING.remove(sender.getUuid());
        if (targets == null) return;
        long revision = System.currentTimeMillis();
        int delivered = 0;
        int skipped = 0;
        String legacyJson = null;
        for (UUID id : targets) {
            ServerPlayerEntity target = sender.getServer().getPlayerManager().getPlayer(id);
            if (target == null) { skipped++; continue; }
            if (ServerPlayNetworking.canSend(target, CommandPanelPackets.SharedPanelChunkS2C.ID)) {
                sendSharedChunks(target, sender.getName().getString(), revision, json);
                LAST_SHARED_REVISION.put(target.getUuid(), revision);
                delivered++;
                continue;
            }
            String payload = json;
            if (payload.length() > 32767 || !hasLegacyProjection(payload)) {
                if (legacyJson == null) legacyJson = legacyProjection(json);
                payload = legacyJson;
            }
            if (payload != null && payload.length() <= 32767
                    && ServerPlayNetworking.canSend(target, CommandPanelPackets.SharedPanelS2C.ID)) {
                ServerPlayNetworking.send(target, new CommandPanelPackets.SharedPanelS2C(sender.getName().getString(), revision, payload));
                LAST_SHARED_REVISION.put(target.getUuid(), revision);
                delivered++;
            } else {
                skipped++;
            }
        }
        if (skipped > 0) sender.sendMessage(Text.literal("已同步给 " + delivered + " 名玩家；" + skipped
                + " 名玩家未收到配置（离线或旧客户端配置超过 32767 字符）。"), false);
    }

    private static void sendSharedChunks(ServerPlayerEntity target, String sourceName, long revision, String json) {
        List<String> chunks = split(json);
        String transferId = UUID.randomUUID().toString();
        for (int i = 0; i < chunks.size(); i++) {
            ServerPlayNetworking.send(target, new CommandPanelPackets.SharedPanelChunkS2C(
                    sourceName, revision, transferId, i, chunks.size(), chunks.get(i)));
        }
    }

    private static List<String> split(String value) {
        List<String> chunks = new ArrayList<>();
        for (int start = 0; start < value.length();) {
            int end = Math.min(value.length(), start + CHUNK_LENGTH);
            if (end < value.length() && end > start && Character.isHighSurrogate(value.charAt(end - 1))
                    && Character.isLowSurrogate(value.charAt(end))) end--;
            chunks.add(value.substring(start, end));
            start = end;
        }
        if (chunks.isEmpty()) chunks.add("");
        return chunks;
    }

    private static String legacyProjection(String json) {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (root.isJsonArray()) return json;
            JsonObject source = root.getAsJsonObject();
            JsonArray popups = source.has("popups") && source.get("popups").isJsonArray()
                    ? source.getAsJsonArray("popups") : null;
            if (popups == null && source.has("panels") && source.get("panels").isJsonArray()) {
                String activeId = source.has("activePanelId") ? source.get("activePanelId").getAsString() : "";
                for (JsonElement panelElement : source.getAsJsonArray("panels")) {
                    if (!panelElement.isJsonObject()) continue;
                    JsonObject panel = panelElement.getAsJsonObject();
                    if (panel.has("id") && activeId.equals(panel.get("id").getAsString())
                            && panel.has("popups") && panel.get("popups").isJsonArray()) {
                        popups = panel.getAsJsonArray("popups");
                        break;
                    }
                }
            }
            if (popups == null) return null;
            JsonObject legacy = new JsonObject();
            legacy.addProperty("version", 2);
            legacy.addProperty("revision", source.has("revision") ? source.get("revision").getAsLong() : 0);
            legacy.add("popups", popups.deepCopy());
            return GSON.toJson(legacy);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean hasLegacyProjection(String json) {
        try {
            JsonElement root = JsonParser.parseString(json);
            return root.isJsonArray() || (root.isJsonObject() && root.getAsJsonObject().has("popups")
                    && root.getAsJsonObject().get("popups").isJsonArray());
        } catch (Exception ignored) {
            return false;
        }
    }

    private record UploadKey(UUID sender, String transferId) {}

    private static final class IncomingUpload {
        private final String[] chunks;
        private int received;
        private int length;
        private long lastUpdated;

        private IncomingUpload(int count, long now) { chunks = new String[count]; lastUpdated = now; }

        private boolean add(int index, int count, String chunk, long now) {
            if (chunks.length != count) return false;
            if (chunks[index] != null) return chunks[index].equals(chunk);
            length += chunk.length();
            if (length > MAX_CONFIG_LENGTH) return false;
            chunks[index] = chunk;
            received++;
            lastUpdated = now;
            return true;
        }

        private boolean complete() { return received == chunks.length; }

        private String join() {
            StringBuilder result = new StringBuilder(length);
            for (String chunk : chunks) {
                if (chunk == null) return null;
                result.append(chunk);
            }
            return result.toString();
        }
    }
}
