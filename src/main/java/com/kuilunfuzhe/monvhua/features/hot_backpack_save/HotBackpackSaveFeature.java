package com.kuilunfuzhe.monvhua.features.hot_backpack_save;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.kuilunfuzhe.monvhua.WitchRole;
import com.kuilunfuzhe.monvhua.network.hot_backpack_save.HotBackpackPackets;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
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
import java.util.concurrent.CompletableFuture;

public final class HotBackpackSaveFeature {
    private static final String SPECIAL_SAVE_TAG = "save_backpack";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int INVENTORY_SLOT_COUNT = 41;
    private static final int MAX_HISTORY_PER_PLAYER = 48;
    private static final String UNDOABLE_REASON_PREFIX = "undoable:";
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
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, damageAmount) -> {
            if (!(entity instanceof ServerPlayerEntity player) || !hasSpecialSaveTag(player)) {
                return true;
            }
            MinecraftServer server = player.getServer();
            if (server == null) return true;
            load(server);
            Snapshot death = capture(player, "tag-death-save");
            death.effects.clear();
            death.health = 1.0F;
            addSnapshot(player, death);
            save(server);
            player.clearStatusEffects();
            player.setHealth(1.0F);
            player.sendMessage(Text.literal("已自动保存死亡前状态，已在原地复活。"), true);
            return false;
        });
        registerReceivers();
    }

    public static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher, Object registryAccess, Object environment) {
        dispatcher.register(CommandManager.literal("monvhua-player-archive-save_玩家存档保存")
                .requires(source -> source.hasPermissionLevel(2))
                .executes(context -> saveSpecialPlayersCommand(context.getSource())));
        dispatcher.register(CommandManager.literal("monvhua-archive")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("save")
                        .then(CommandManager.argument("tag", StringArgumentType.word())
                                .executes(context -> saveTaggedPlayers(context.getSource(), StringArgumentType.getString(context, "tag")))))
                .then(CommandManager.literal("apply")
                        .then(CommandManager.argument("tag", StringArgumentType.word())
                                .executes(context -> applyTaggedPlayers(context.getSource(), StringArgumentType.getString(context, "tag"), null))
                                .then(CommandManager.argument("timestamp", LongArgumentType.longArg(0))
                                        .suggests(HotBackpackSaveFeature::suggestBatchTimestamps)
                                        .executes(context -> applyTaggedPlayers(context.getSource(), StringArgumentType.getString(context, "tag"), LongArgumentType.getLong(context, "timestamp")))))));
    }

    private static CompletableFuture<Suggestions> suggestBatchTimestamps(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
        String tag;
        try {
            tag = StringArgumentType.getString(context, "tag");
        } catch (IllegalArgumentException ignored) {
            return builder.buildFuture();
        }
        load(context.getSource().getServer());
        Set<Long> timestamps = new java.util.TreeSet<>(Comparator.reverseOrder());
        String reason = "tag-batch:" + tag;
        for (PlayerRecord record : store.records.values()) {
            for (Snapshot snapshot : record.history) {
                if (snapshot != null && reason.equals(snapshot.reason)) {
                    timestamps.add(snapshot.timestamp);
                }
            }
        }
        for (Long timestamp : timestamps) {
            builder.suggest(String.valueOf(timestamp), Text.literal(TIME_FORMAT.format(Instant.ofEpochMilli(timestamp))));
        }
        return builder.buildFuture();
    }

    private static int saveTaggedPlayers(ServerCommandSource source, String tag) {
        MinecraftServer server = source.getServer();
        load(server);
        long timestamp = System.currentTimeMillis();
        int count = 0;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!player.getCommandTags().contains(tag)) continue;
            Snapshot snapshot = capture(player, "tag-batch:" + tag);
            snapshot.timestamp = timestamp;
            addSnapshot(player, snapshot);
            count++;
        }
        store.guardedApply.put(tag, false);
        save(server);
        int saved = count;
        source.sendFeedback(() -> Text.literal("已为标签 " + tag + " 保存 " + saved + " 名玩家，批次时间戳：" + timestamp), true);
        return count;
    }

    private static int applyTaggedPlayers(ServerCommandSource source, String tag, Long requestedTimestamp) {
        MinecraftServer server = source.getServer();
        load(server);
        boolean explicitTimestamp = requestedTimestamp != null;
        if (!explicitTimestamp && Boolean.TRUE.equals(store.guardedApply.get(tag))) {
            source.sendError(Text.literal("该标签的无时间戳覆盖已被保护；请明确输入时间戳，或先重新备份。"));
            return 0;
        }
        long timestamp = explicitTimestamp ? requestedTimestamp : latestBatchTimestamp(tag);
        if (timestamp < 0) {
            source.sendError(Text.literal("没有找到标签 " + tag + " 的批次存档。"));
            return 0;
        }
        int applied = 0;
        int skipped = 0;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!player.getCommandTags().contains(tag)) continue;
            Snapshot snapshot = findSnapshot(player.getUuid(), timestamp);
            if (snapshot == null || !("tag-batch:" + tag).equals(snapshot.reason)) {
                skipped++;
                continue;
            }
            applySnapshotToOnlinePlayer(player, snapshot, true);
            applied++;
        }
        store.guardedApply.put(tag, !explicitTimestamp);
        save(server);
        syncAll(server);
        int restored = applied;
        int ignored = skipped;
        source.sendFeedback(() -> Text.literal("批量恢复完成：应用 " + restored + " 人，忽略无此批次存档的玩家 " + ignored + " 人。"), true);
        return applied;
    }

    private static long latestBatchTimestamp(String tag) {
        long latest = -1L;
        String reason = "tag-batch:" + tag;
        for (PlayerRecord record : store.records.values()) {
            for (Snapshot snapshot : record.history) {
                if (snapshot != null && reason.equals(snapshot.reason)) {
                    latest = Math.max(latest, snapshot.timestamp);
                }
            }
        }
        return latest;
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
        store.guardedApply.put(SPECIAL_SAVE_TAG, false);
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
        addSnapshot(player, snapshot);
    }

    private static void addSnapshot(ServerPlayerEntity player, Snapshot snapshot) {
        PlayerRecord record = store.records.computeIfAbsent(player.getUuid().toString(), ignored -> new PlayerRecord());
        record.uuid = player.getUuid().toString();
        record.name = player.getName().getString();
        record.roleTag = snapshot.roleTag;
        record.online = true;
        snapshot.uuid = record.uuid;
        snapshot.name = record.name;
        snapshot.roleTag = record.roleTag;
        record.history.add(0, snapshot);
        record.history.sort(Comparator.comparingLong((Snapshot s) -> s.timestamp).reversed());
        while (record.history.size() > MAX_HISTORY_PER_PLAYER) {
            record.history.remove(record.history.size() - 1);
        }
    }

    private static void addUndoableSnapshot(ServerPlayerEntity player, String action) {
        Snapshot snapshot = capture(player, UNDOABLE_REASON_PREFIX + action);
        store.undo.put(player.getUuid().toString(), snapshot);
        addSnapshot(player, snapshot);
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
        snapshot.dimension = player.getWorld().getRegistryKey().getValue().toString();
        snapshot.x = player.getX();
        snapshot.y = player.getY();
        snapshot.z = player.getZ();
        snapshot.yaw = player.getYaw();
        snapshot.pitch = player.getPitch();
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
            addUndoableSnapshot(target, "apply-snapshot");
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
        Identifier dimension = Identifier.tryParse(snapshot.dimension);
        if (dimension != null) {
            var world = target.getServer().getWorld(RegistryKey.of(RegistryKeys.WORLD, dimension));
            if (world != null) {
                target.teleport(world, snapshot.x, snapshot.y, snapshot.z, Set.of(), snapshot.yaw, snapshot.pitch, false);
            }
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
        Snapshot undo = popLatestUndoableSnapshot(targetUuid.toString());
        if (undo == null) {
            undo = store.undo.remove(targetUuid.toString());
        } else {
            store.undo.remove(targetUuid.toString());
        }
        if (undo == null) {
            actor.sendMessage(Text.literal("§c没有可撤回的粘贴记录"), true);
            return;
        }
        ServerPlayerEntity target = server.getPlayerManager().getPlayer(targetUuid);
        if (target != null) {
            addUndoableSnapshot(target, "before-undo");
            applySnapshotToOnlinePlayer(target, undo, false);
            actor.sendMessage(Text.literal("§a已撤回到粘贴前状态"), true);
        } else {
            store.pendingApply.put(targetUuid.toString(), undo);
            actor.sendMessage(Text.literal("§e目标离线，已设置下次上线撤回"), true);
        }
        save(server);
        syncAll(server);
    }

    private static Snapshot popLatestUndoableSnapshot(String uuid) {
        PlayerRecord record = store.records.get(uuid);
        if (record == null || record.history == null) {
            return null;
        }
        record.history.sort(Comparator.comparingLong((Snapshot s) -> s.timestamp).reversed());
        for (int i = 0; i < record.history.size(); i++) {
            Snapshot snapshot = record.history.get(i);
            if (snapshot != null && snapshot.reason != null && snapshot.reason.startsWith(UNDOABLE_REASON_PREFIX)) {
                record.history.remove(i);
                return snapshot;
            }
        }
        return null;
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
        snapshot.reason = "edit-archive-slot:" + slot;
        snapshot.items.set(slot, itemNbtJson == null ? "" : itemNbtJson);
        save(server);
        syncAll(server);
    }

    private static void editOwnSlot(ServerPlayerEntity player, int slot, String itemNbtJson) {
        if (slot < 0 || slot >= INVENTORY_SLOT_COUNT) {
            return;
        }
        addUndoableSnapshot(player, "edit-own-slot:" + slot);
        setSlotStack(player, slot, itemFromJson(itemNbtJson));
        player.getInventory().markDirty();
        player.playerScreenHandler.sendContentUpdates();
        player.currentScreenHandler.sendContentUpdates();
        MinecraftServer server = player.getServer();
        if (server != null) {
            save(server);
            syncAll(server);
        }
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
        public Map<String, Boolean> guardedApply = new LinkedHashMap<>();

        Store sanitized() {
            if (records == null) records = new LinkedHashMap<>();
            if (pendingApply == null) pendingApply = new LinkedHashMap<>();
            if (undo == null) undo = new LinkedHashMap<>();
            if (guardedApply == null) guardedApply = new LinkedHashMap<>();
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
        public String dimension = "minecraft:overworld";
        public double x;
        public double y;
        public double z;
        public float yaw;
        public float pitch;

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
