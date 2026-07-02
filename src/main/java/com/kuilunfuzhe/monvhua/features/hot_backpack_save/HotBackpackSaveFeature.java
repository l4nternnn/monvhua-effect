package com.kuilunfuzhe.monvhua.features.hot_backpack_save;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.kuilunfuzhe.monvhua.WitchRole;
import com.kuilunfuzhe.monvhua.network.hot_backpack_save.HotBackpackPackets;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.GameMode;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class HotBackpackSaveFeature {
    private static final String SPECIAL_SAVE_TAG = "save_backpack";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int INVENTORY_SLOT_COUNT = 41;
    private static final int MAX_HISTORY_PER_PLAYER = 48;
    private static final Set<String> ROLE_TAGS = new LinkedHashSet<>();
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withLocale(Locale.ROOT)
            .withZone(ZoneId.systemDefault());

    private static Store store = new Store();
    private static MinecraftServer activeServer;
    private static boolean initialized;

    static {
        for (WitchRole role : WitchRole.values()) {
            ROLE_TAGS.add(role.id);
        }
    }

    private HotBackpackSaveFeature() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            activeServer = server;
            load(server);
            ServerPlayerEntity player = handler.getPlayer();
            PlayerRecord record = store.records.get(player.getUuid().toString());
            if (record != null) {
                record.name = player.getName().getString();
                record.online = true;
                record.roleTag = firstRoleTag(player);
            }
            Snapshot pending = store.pendingApply.remove(player.getUuid().toString());
            if (pending != null) {
                applySnapshotToOnlinePlayer(player, pending, true);
                player.sendMessage(Text.literal("§a已应用离线期间等待的玩家存档"), true);
            }
            save(server);
            syncTo(player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            PlayerRecord record = store.records.get(player.getUuid().toString());
            if (record != null) {
                record.online = false;
                save(server);
            }
        });
        registerReceivers();
    }

    public static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher, Object registryAccess, Object environment) {
        dispatcher.register(CommandManager.literal("monvhua_player_archive_save")
                .requires(source -> source.hasPermissionLevel(2))
                .executes(context -> saveSpecialPlayersCommand(context.getSource())));
        dispatcher.register(CommandManager.literal("monvhua玩家存档保存")
                .requires(source -> source.hasPermissionLevel(2))
                .executes(context -> saveSpecialPlayersCommand(context.getSource())));
    }

    private static int saveSpecialPlayersCommand(ServerCommandSource source) {
        MinecraftServer server = source.getServer();
        load(server);
        int count = saveSpecialPlayers(server);
        source.sendFeedback(() -> Text.literal("§a已保存 " + count + " 个 save_backpack 玩家存档"), true);
        return count;
    }

    private static void registerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(HotBackpackPackets.RequestStateC2S.ID, (packet, context) ->
                context.server().execute(() -> {
                    activeServer = context.server();
                    load(context.server());
                    syncTo(context.player());
                }));
        ServerPlayNetworking.registerGlobalReceiver(HotBackpackPackets.SaveSpecialPlayersC2S.ID, (packet, context) ->
                context.server().execute(() -> {
                    if (!canManage(context.player())) {
                        return;
                    }
                    int count = saveSpecialPlayers(context.server());
                    context.player().sendMessage(Text.literal("§a已保存 " + count + " 个 save_backpack 玩家存档"), true);
                    syncAll(context.server());
                }));
        ServerPlayNetworking.registerGlobalReceiver(HotBackpackPackets.SaveAllPlayersC2S.ID, (packet, context) ->
                context.server().execute(() -> {
                    if (!canManage(context.player())) {
                        return;
                    }
                    int count = saveAllPlayers(context.server());
                    context.player().sendMessage(Text.literal("§a已保存 " + count + " 个当前在线玩家存档"), true);
                    syncAll(context.server());
                }));
        ServerPlayNetworking.registerGlobalReceiver(HotBackpackPackets.ApplySnapshotC2S.ID, (packet, context) ->
                context.server().execute(() -> {
                    if (!canManage(context.player())) {
                        return;
                    }
                    applySnapshot(context.server(), context.player(), packet.sourceUuid(), packet.timestamp(), packet.targetUuid());
                }));
        ServerPlayNetworking.registerGlobalReceiver(HotBackpackPackets.ApplySnapshotToSelfC2S.ID, (packet, context) ->
                context.server().execute(() -> applySnapshot(context.server(), context.player(), packet.sourceUuid(), packet.timestamp(), context.player().getUuid())));
        ServerPlayNetworking.registerGlobalReceiver(HotBackpackPackets.UndoApplyC2S.ID, (packet, context) ->
                context.server().execute(() -> {
                    if (!canManage(context.player()) && !context.player().getUuid().equals(packet.targetUuid())) {
                        return;
                    }
                    undoApply(context.server(), context.player(), packet.targetUuid());
                }));
        ServerPlayNetworking.registerGlobalReceiver(HotBackpackPackets.EditPreviewSlotC2S.ID, (packet, context) ->
                context.server().execute(() -> {
                    if (!canManage(context.player())) {
                        return;
                    }
                    editSnapshotSlot(context.server(), context.player(), packet.sourceUuid(), packet.timestamp(), packet.slot(), packet.itemNbtJson());
                }));
        ServerPlayNetworking.registerGlobalReceiver(HotBackpackPackets.EditOwnSlotC2S.ID, (packet, context) ->
                context.server().execute(() -> {
                    if (!canManage(context.player())) {
                        return;
                    }
                    editOwnSlot(context.player(), packet.slot(), packet.itemNbtJson());
                }));
    }

    private static boolean canManage(ServerPlayerEntity player) {
        return player.hasPermissionLevel(2) || player.isCreative();
    }

    private static int saveSpecialPlayers(MinecraftServer server) {
        activeServer = server;
        load(server);
        int count = 0;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!hasSpecialSaveTag(player)) {
                continue;
            }
            addSnapshot(player, "special-tag-save");
            count++;
        }
        save(server);
        return count;
    }

    private static int saveAllPlayers(MinecraftServer server) {
        activeServer = server;
        load(server);
        int count = 0;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            addSnapshot(player, "all-player-save");
            count++;
        }
        save(server);
        return count;
    }

    private static boolean hasSpecialSaveTag(ServerPlayerEntity player) {
        return player.getCommandTags().contains(SPECIAL_SAVE_TAG);
    }

    private static void addSnapshot(ServerPlayerEntity player, String reason) {
        Snapshot snapshot = capture(player, reason);
        PlayerRecord record = store.records.computeIfAbsent(player.getUuid().toString(), ignored -> new PlayerRecord());
        record.uuid = player.getUuid().toString();
        record.name = player.getName().getString();
        record.roleTag = snapshot.roleTag;
        record.online = true;
        record.history.add(0, snapshot);
        record.history.sort(Comparator.comparingLong((Snapshot s) -> s.timestamp).reversed());
        while (record.history.size() > MAX_HISTORY_PER_PLAYER) {
            record.history.remove(record.history.size() - 1);
        }
    }

    private static Snapshot capture(ServerPlayerEntity player, String reason) {
        Snapshot snapshot = new Snapshot();
        snapshot.uuid = player.getUuid().toString();
        snapshot.name = player.getName().getString();
        snapshot.timestamp = System.currentTimeMillis();
        snapshot.reason = reason;
        snapshot.roleTag = firstRoleTag(player);
        snapshot.items = new ArrayList<>(INVENTORY_SLOT_COUNT);
        for (int i = 0; i < INVENTORY_SLOT_COUNT; i++) {
            snapshot.items.add(itemToJson(slotStack(player, i)));
        }
        snapshot.tags = new ArrayList<>(player.getCommandTags());
        snapshot.scoreboard = scoreboardValues(player);
        snapshot.effects = statusEffects(player);
        snapshot.health = player.getHealth();
        snapshot.absorption = player.getAbsorptionAmount();
        snapshot.food = player.getHungerManager().getFoodLevel();
        snapshot.saturation = player.getHungerManager().getSaturationLevel();
        snapshot.experienceLevel = player.experienceLevel;
        snapshot.experienceProgress = player.experienceProgress;
        snapshot.totalExperience = player.totalExperience;
        snapshot.selectedSlot = player.getInventory().getSelectedSlot();
        snapshot.gameMode = player.interactionManager.getGameMode().name();
        return snapshot;
    }

    private static ItemStack slotStack(ServerPlayerEntity player, int slot) {
        if (slot >= 0 && slot < 36) {
            return player.getInventory().getStack(slot);
        }
        return switch (slot) {
            case 36 -> player.getEquippedStack(EquipmentSlot.FEET);
            case 37 -> player.getEquippedStack(EquipmentSlot.LEGS);
            case 38 -> player.getEquippedStack(EquipmentSlot.CHEST);
            case 39 -> player.getEquippedStack(EquipmentSlot.HEAD);
            case 40 -> player.getEquippedStack(EquipmentSlot.OFFHAND);
            default -> ItemStack.EMPTY;
        };
    }

    private static void setSlotStack(ServerPlayerEntity player, int slot, ItemStack stack) {
        ItemStack copy = stack == null ? ItemStack.EMPTY : stack.copy();
        if (slot >= 0 && slot < 36) {
            player.getInventory().setStack(slot, copy);
            return;
        }
        switch (slot) {
            case 36 -> player.equipStack(EquipmentSlot.FEET, copy);
            case 37 -> player.equipStack(EquipmentSlot.LEGS, copy);
            case 38 -> player.equipStack(EquipmentSlot.CHEST, copy);
            case 39 -> player.equipStack(EquipmentSlot.HEAD, copy);
            case 40 -> player.equipStack(EquipmentSlot.OFFHAND, copy);
            default -> {
            }
        }
    }

    private static String itemToJson(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        return ItemStack.CODEC.encodeStart(JsonOps.INSTANCE, stack)
                .result()
                .map(GSON::toJson)
                .orElse("");
    }

    private static ItemStack itemFromJson(String json) {
        if (json == null || json.isBlank()) {
            return ItemStack.EMPTY;
        }
        try {
            return ItemStack.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                    .result()
                    .orElse(ItemStack.EMPTY);
        } catch (Exception e) {
            MonvhuaMod.LOGGER.warn("[Monvhua] Failed to parse archived item stack", e);
            return ItemStack.EMPTY;
        }
    }

    private static Map<String, Integer> scoreboardValues(ServerPlayerEntity player) {
        Map<String, Integer> values = new LinkedHashMap<>();
        Scoreboard scoreboard = player.getScoreboard();
        for (ScoreboardObjective objective : scoreboard.getObjectives()) {
            var score = scoreboard.getScore(player, objective);
            if (score != null) {
                values.put(objective.getName(), score.getScore());
            }
        }
        return values;
    }

    private static List<EffectRecord> statusEffects(ServerPlayerEntity player) {
        List<EffectRecord> effects = new ArrayList<>();
        Collection<StatusEffectInstance> active = player.getActiveStatusEffects().values();
        for (StatusEffectInstance effect : active) {
            Identifier id = Registries.STATUS_EFFECT.getId(effect.getEffectType().value());
            if (id == null) {
                continue;
            }
            EffectRecord record = new EffectRecord();
            record.id = id.toString();
            record.duration = effect.getDuration();
            record.amplifier = effect.getAmplifier();
            record.ambient = effect.isAmbient();
            record.showParticles = effect.shouldShowParticles();
            record.showIcon = effect.shouldShowIcon();
            effects.add(record);
        }
        return effects;
    }

    private static String firstRoleTag(ServerPlayerEntity player) {
        for (String tag : ROLE_TAGS) {
            if (player.getCommandTags().contains(tag)) {
                return tag;
            }
        }
        return null;
    }

    private static void applySnapshot(MinecraftServer server, ServerPlayerEntity actor, UUID sourceUuid, long timestamp, UUID targetUuid) {
        load(server);
        Snapshot snapshot = findSnapshot(sourceUuid, timestamp);
        if (snapshot == null) {
            actor.sendMessage(Text.literal("§c没有找到对应时间的玩家存档"), true);
            return;
        }
        ServerPlayerEntity target = server.getPlayerManager().getPlayer(targetUuid);
        if (target != null) {
            applySnapshotToOnlinePlayer(target, snapshot, true);
            actor.sendMessage(Text.literal("§a已应用玩家存档到 " + target.getName().getString()), true);
        } else {
            store.pendingApply.put(targetUuid.toString(), copySnapshot(snapshot));
            PlayerRecord record = store.records.computeIfAbsent(targetUuid.toString(), ignored -> new PlayerRecord());
            record.uuid = targetUuid.toString();
            if (record.name == null || record.name.isBlank()) {
                record.name = "离线玩家 " + shortUuid(targetUuid);
            }
            record.online = false;
            actor.sendMessage(Text.literal("§e目标离线，已设置为下次上线应用"), true);
        }
        save(server);
        syncAll(server);
    }

    private static void applySnapshotToOnlinePlayer(ServerPlayerEntity target, Snapshot snapshot, boolean pushUndo) {
        if (pushUndo) {
            store.undo.put(target.getUuid().toString(), capture(target, "undo-before-apply"));
        }
        for (int i = 0; i < INVENTORY_SLOT_COUNT; i++) {
            String json = i < snapshot.items.size() ? snapshot.items.get(i) : "";
            setSlotStack(target, i, itemFromJson(json));
        }
        target.getInventory().markDirty();

        for (String tag : new ArrayList<>(target.getCommandTags())) {
            if (ROLE_TAGS.contains(tag) || (snapshot.tags != null && !snapshot.tags.contains(tag))) {
                target.removeCommandTag(tag);
            }
        }
        if (snapshot.tags != null) {
            for (String tag : snapshot.tags) {
                if (tag != null && !tag.isBlank()) {
                    target.addCommandTag(tag);
                }
            }
        }

        Scoreboard scoreboard = target.getScoreboard();
        if (snapshot.scoreboard != null) {
            for (Map.Entry<String, Integer> entry : snapshot.scoreboard.entrySet()) {
                ScoreboardObjective objective = scoreboard.getNullableObjective(entry.getKey());
                if (objective != null) {
                    scoreboard.getOrCreateScore(target, objective).setScore(entry.getValue());
                }
            }
        }

        target.clearStatusEffects();
        if (snapshot.effects != null) {
            for (EffectRecord record : snapshot.effects) {
                Identifier id = Identifier.tryParse(record.id);
                if (id == null) {
                    continue;
                }
                Optional<RegistryEntry.Reference<StatusEffect>> entry = Registries.STATUS_EFFECT.getEntry(id);
                entry.ifPresent(effect -> target.addStatusEffect(new StatusEffectInstance(
                        effect,
                        Math.max(1, record.duration),
                        Math.max(0, record.amplifier),
                        record.ambient,
                        record.showParticles,
                        record.showIcon
                )));
            }
        }

        target.setHealth(Math.clamp(snapshot.health, 1.0F, target.getMaxHealth()));
        target.setAbsorptionAmount(Math.max(0.0F, snapshot.absorption));
        target.getHungerManager().setFoodLevel(Math.clamp(snapshot.food, 0, 20));
        target.getHungerManager().setSaturationLevel(Math.max(0.0F, snapshot.saturation));
        target.experienceLevel = Math.max(0, snapshot.experienceLevel);
        target.experienceProgress = Math.clamp(snapshot.experienceProgress, 0.0F, 1.0F);
        target.totalExperience = Math.max(0, snapshot.totalExperience);
        GameMode mode = gameMode(snapshot.gameMode);
        if (mode != null) {
            target.changeGameMode(mode);
        }

        PlayerRecord targetRecord = store.records.computeIfAbsent(target.getUuid().toString(), ignored -> new PlayerRecord());
        targetRecord.uuid = target.getUuid().toString();
        targetRecord.name = target.getName().getString();
        targetRecord.roleTag = firstRoleTag(target);
        targetRecord.online = true;
    }

    private static GameMode gameMode(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        for (GameMode mode : GameMode.values()) {
            if (mode.name().equalsIgnoreCase(name)) {
                return mode;
            }
        }
        return null;
    }

    private static void undoApply(MinecraftServer server, ServerPlayerEntity actor, UUID targetUuid) {
        load(server);
        Snapshot undo = store.undo.remove(targetUuid.toString());
        if (undo == null) {
            actor.sendMessage(Text.literal("§c没有可撤回的粘贴记录"), true);
            return;
        }
        ServerPlayerEntity target = server.getPlayerManager().getPlayer(targetUuid);
        if (target != null) {
            applySnapshotToOnlinePlayer(target, undo, false);
            actor.sendMessage(Text.literal("§a已撤回到粘贴前状态"), true);
        } else {
            store.pendingApply.put(targetUuid.toString(), undo);
            actor.sendMessage(Text.literal("§e目标离线，已设置下次上线撤回"), true);
        }
        save(server);
        syncAll(server);
    }

    private static void editSnapshotSlot(MinecraftServer server, ServerPlayerEntity actor, UUID sourceUuid, long timestamp, int slot, String itemNbtJson) {
        if (slot < 0 || slot >= INVENTORY_SLOT_COUNT) {
            return;
        }
        load(server);
        Snapshot snapshot = findSnapshot(sourceUuid, timestamp);
        if (snapshot == null) {
            actor.sendMessage(Text.literal("§c没有找到可编辑的存档"), true);
            return;
        }
        while (snapshot.items.size() < INVENTORY_SLOT_COUNT) {
            snapshot.items.add("");
        }
        snapshot.items.set(slot, itemNbtJson == null ? "" : itemNbtJson);
        save(server);
        syncAll(server);
    }

    private static void editOwnSlot(ServerPlayerEntity player, int slot, String itemNbtJson) {
        if (slot < 0 || slot >= INVENTORY_SLOT_COUNT) {
            return;
        }
        setSlotStack(player, slot, itemFromJson(itemNbtJson));
        player.getInventory().markDirty();
        player.playerScreenHandler.sendContentUpdates();
        player.currentScreenHandler.sendContentUpdates();
    }

    private static Snapshot findSnapshot(UUID uuid, long timestamp) {
        PlayerRecord record = store.records.get(uuid.toString());
        if (record == null) {
            return null;
        }
        for (Snapshot snapshot : record.history) {
            if (snapshot.timestamp == timestamp) {
                return snapshot;
            }
        }
        return null;
    }

    private static Snapshot copySnapshot(Snapshot snapshot) {
        return GSON.fromJson(GSON.toJson(snapshot), Snapshot.class);
    }

    private static void syncTo(ServerPlayerEntity player) {
        load(player.getServer());
        markOnlinePlayers(player.getServer());
        ServerPlayNetworking.send(player, new HotBackpackPackets.StateS2C(GSON.toJson(store)));
    }

    private static void syncAll(MinecraftServer server) {
        load(server);
        markOnlinePlayers(server);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, new HotBackpackPackets.StateS2C(GSON.toJson(store)));
        }
    }

    private static void markOnlinePlayers(MinecraftServer server) {
        for (PlayerRecord record : store.records.values()) {
            record.online = false;
        }
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            PlayerRecord record = store.records.computeIfAbsent(player.getUuid().toString(), ignored -> new PlayerRecord());
            record.uuid = player.getUuid().toString();
            record.name = player.getName().getString();
            record.online = true;
            record.roleTag = firstRoleTag(player);
        }
    }

    private static void load(MinecraftServer server) {
        if (server == null) {
            return;
        }
        activeServer = server;
        Path path = path(server);
        if (!Files.isRegularFile(path)) {
            store = new Store();
            return;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Store loaded = GSON.fromJson(reader, Store.class);
            store = loaded == null ? new Store() : loaded.sanitized();
        } catch (Exception e) {
            MonvhuaMod.LOGGER.warn("[Monvhua] Failed to load hot backpack save store", e);
            store = new Store();
        }
    }

    private static void save(MinecraftServer server) {
        if (server == null) {
            return;
        }
        try {
            Path path = path(server);
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(store.sanitized(), writer);
            }
        } catch (IOException e) {
            MonvhuaMod.LOGGER.warn("[Monvhua] Failed to save hot backpack save store", e);
        }
    }

    private static Path path(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("monvhua_hot_backpack_save.json");
    }

    private static String shortUuid(UUID uuid) {
        String raw = uuid.toString();
        return raw.substring(0, Math.min(8, raw.length()));
    }

    public static final class Store {
        public Map<String, PlayerRecord> records = new LinkedHashMap<>();
        public Map<String, Snapshot> pendingApply = new LinkedHashMap<>();
        public Map<String, Snapshot> undo = new LinkedHashMap<>();

        Store sanitized() {
            if (records == null) records = new LinkedHashMap<>();
            if (pendingApply == null) pendingApply = new LinkedHashMap<>();
            if (undo == null) undo = new LinkedHashMap<>();
            for (PlayerRecord record : records.values()) {
                record.sanitized();
            }
            return this;
        }
    }

    public static final class PlayerRecord {
        public String uuid = "";
        public String name = "";
        public String roleTag = "";
        public boolean online;
        public List<Snapshot> history = new ArrayList<>();

        PlayerRecord sanitized() {
            if (history == null) history = new ArrayList<>();
            history.removeIf(snapshot -> snapshot == null);
            for (Snapshot snapshot : history) {
                snapshot.sanitized();
            }
            return this;
        }
    }

    public static final class Snapshot {
        public String uuid = "";
        public String name = "";
        public String roleTag = "";
        public long timestamp;
        public String reason = "";
        public List<String> items = new ArrayList<>();
        public List<String> tags = new ArrayList<>();
        public Map<String, Integer> scoreboard = new LinkedHashMap<>();
        public List<EffectRecord> effects = new ArrayList<>();
        public float health = 20.0F;
        public float absorption;
        public int food = 20;
        public float saturation = 5.0F;
        public int experienceLevel;
        public float experienceProgress;
        public int totalExperience;
        public int selectedSlot;
        public String gameMode = "survival";

        Snapshot sanitized() {
            if (items == null) items = new ArrayList<>();
            while (items.size() < INVENTORY_SLOT_COUNT) items.add("");
            if (tags == null) tags = new ArrayList<>();
            if (scoreboard == null) scoreboard = new LinkedHashMap<>();
            if (effects == null) effects = new ArrayList<>();
            return this;
        }

        public String displayTime() {
            return TIME_FORMAT.format(Instant.ofEpochMilli(timestamp));
        }
    }

    public static final class EffectRecord {
        public String id = "";
        public int duration;
        public int amplifier;
        public boolean ambient;
        public boolean showParticles = true;
        public boolean showIcon = true;
    }
}
