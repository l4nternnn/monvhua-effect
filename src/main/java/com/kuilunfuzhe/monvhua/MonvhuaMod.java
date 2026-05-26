package com.kuilunfuzhe.monvhua;

import com.google.gson.JsonObject;
import com.kuilunfuzhe.monvhua.command.*;
import com.kuilunfuzhe.monvhua.config.GlobalConfigManager;
import com.kuilunfuzhe.monvhua.effect.DisplayOnlyEffect;
import com.kuilunfuzhe.monvhua.entity.ModBlockEntities;
import com.kuilunfuzhe.monvhua.features.block.body.BodyPartManager;
import com.kuilunfuzhe.monvhua.features.carryentity.CarryEvents;
import com.kuilunfuzhe.monvhua.features.evil_eyes.Evil_Eyes;
import com.kuilunfuzhe.monvhua.features.evil_eyes.ViewingModeBlocker;
import com.kuilunfuzhe.monvhua.features.evil_eyes.server.CameraWatchManager;
import com.kuilunfuzhe.monvhua.features.guidance.Gazeguidance;
import com.kuilunfuzhe.monvhua.item.config.GazeConfig;
import com.kuilunfuzhe.monvhua.item.gazeguidance.ModItems;
import com.kuilunfuzhe.monvhua.item.mirror.mirror_of_then_and_now;
import com.kuilunfuzhe.monvhua.item.modblock.ModBlocks;
import com.kuilunfuzhe.monvhua.item.modblock.moditems.Assembly_ModItems;
import com.kuilunfuzhe.monvhua.network.ModNetworking;
import com.kuilunfuzhe.monvhua.network.camerawatch.*;
import com.kuilunfuzhe.monvhua.network.clairvoyance.*;
import com.kuilunfuzhe.monvhua.network.gazeguidance.*;
import com.kuilunfuzhe.monvhua.network.mirror.MirrorStateS2CPacket;
import com.kuilunfuzhe.monvhua.network.mirror.MirrorToggleC2SPacket;
import com.kuilunfuzhe.monvhua.network.openback.CarryEntityPayload;
import com.kuilunfuzhe.monvhua.network.openback.OpenOtherInventoryPayload;
import com.kuilunfuzhe.monvhua.network.openback.PlaceCarriedEntityPayload;
import com.kuilunfuzhe.monvhua.screen.ModScreenHandlers;
import com.kuilunfuzhe.monvhua.screen.OtherPlayerInventoryScreenHandler;
import com.kuilunfuzhe.monvhua.screen.OtherPlayerInventoryScreenHandlerFactory;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MonvhuaMod implements ModInitializer {
    public static final String MOD_ID = "monvhua";
    public static final String OBJECTIVE_NAME = "monvhua";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // ===== monvhua effect system =====
    public static final EnumMap<WitchRole, EnumMap<WitchStage, RegistryEntry<StatusEffect>>> EFFECTS =
            new EnumMap<>(WitchRole.class);
    private static final Map<UUID, RegistryEntry<StatusEffect>> lastEffect = new HashMap<>();

    // Tainted stage random scheduling
    private static final Random RANDOM = new Random();
    private static final Map<UUID, List<String>> pendingTainted = new HashMap<>();
    private static final Map<UUID, Integer> pendingTaintedDelay = new HashMap<>();

    // Floating flight ability tracking
    private static final Set<UUID> floatingPlayers = new HashSet<>();

    // ===== clairvoyance shared state =====
    public static ScreenHandlerType<OtherPlayerInventoryScreenHandler> OTHER_INVENTORY_HANDLER =
            new ScreenHandlerType<>(OtherPlayerInventoryScreenHandler::new, FeatureSet.empty());
    public static final Map<ServerPlayerEntity, ServerPlayerEntity> VIEWING_MAP = new ConcurrentHashMap<>();
    public static final Map<UUID, String> VIEW_MODE_PREFERENCE = new ConcurrentHashMap<>();

    private int tickCounter = 0;
    private static boolean DEBUG_LOGGED = false;

    @Override
    public void onInitialize() {
        // ===== 1. Register 112 DisplayOnlyEffect instances =====
        int count = 0;
        for (WitchRole role : WitchRole.values()) {
            EnumMap<WitchStage, RegistryEntry<StatusEffect>> roleMap = new EnumMap<>(WitchStage.class);
            for (WitchStage stage : WitchStage.values()) {
                String effectId = role.id + "_" + stage.id;
                RegistryEntry<StatusEffect> holder = Registry.registerReference(
                        Registries.STATUS_EFFECT,
                        Identifier.of(MOD_ID, effectId),
                        new DisplayOnlyEffect(stage.category, stage.color)
                );
                roleMap.put(stage, holder);
                count++;
            }
            EFFECTS.put(role, roleMap);
        }
        LOGGER.info("[{}] Registered {} role x stage effects.", MOD_ID, count);

        // ===== 2. Network packets =====
        ModNetworking.registerS2CPackets();
        GlobalConfigS2CPacket.register();
        OpenUIPacket.register();
        EntityMarkedPayload.register();
        ToggleImagesS2CPacket.register();
        SelectViewPayload.register();
        ForceExitViewPayload.register();
        MarkParticleS2CPacket.register();
        SyncConfigS2CPacket.register();
        EnergySyncPacket.register();
        MarkCountPacket.register();
        FocusStatusPacket.register();
        ParticlePacket.register();
        StrengthPacket.register();
        AnchorParticleS2CPacket.register();
        PlayerStageS2CPacket.register();
        ExplosionParticleS2CPacket.register();
        CameraWatchBindS2CPacket.register();
        CameraWatchUnbindS2CPacket.register();
        CameraUpdateS2CPacket.register();
        MirrorStateS2CPacket.register();

        ModNetworking.registerC2SPackets();
        MarkEntityPayload.register();
        ExitViewPayload.register();
        MagicPacket.register();
        RightClickActionPacket.register();
        RequestConfigC2SPacket.register();
        UpdateConfigC2SPacket.register();
        RequestGlobalConfigC2SPacket.register();
        UpdateGlobalConfigC2SPacket.register();
        PlaceParrotC2SPacket.register();
        AnchorDestroyC2SPacket.register();
        OpenOtherInventoryPayload.register();
        CarryEntityPayload.register();
        PlaceCarriedEntityPayload.register();
        CameraWatchStartC2SPacket.register();
        CameraWatchStopC2SPacket.register();
        MirrorToggleC2SPacket.register();

        ServerPlayNetworking.registerGlobalReceiver(MirrorToggleC2SPacket.ID, (packet, context) -> {
            MirrorCommand.toggleViewport(context.player());
        });

        // ===== 3. Commands =====
        CommandRegistrationCallback.EVENT.register(GiveBodyPartCommand::register);
        CommandRegistrationCallback.EVENT.register(ReplaceBodyPartCommand::register);
        CommandRegistrationCallback.EVENT.register(WatchCommand::register);
        CommandRegistrationCallback.EVENT.register(MirrorCommand::register);
        CommandRegistrationCallback.EVENT.register(ClairvoyanceCommand::register);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("clairvoyance-肢体|合并")
                .requires(source -> source.hasPermissionLevel(2))
                .executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayer();
                    if (player == null) return 0;
                    return BodyPartManager.mergeBodyParts(player);
                })
            );
        });

        // ===== 4. Config =====
        GlobalConfigManager configManager = new GlobalConfigManager();

        ServerPlayNetworking.registerGlobalReceiver(RequestGlobalConfigC2SPacket.ID, (packet, context) -> {
            sendGlobalConfigToPlayer(context.player(), configManager);
        });

        ServerPlayNetworking.registerGlobalReceiver(RequestConfigC2SPacket.ID, (packet, context) -> {
            ServerPlayerEntity player = context.player();
            ServerPlayNetworking.send(player, new SyncConfigS2CPacket(GazeConfig.getInstance().toJson()));
        });

        ServerPlayNetworking.registerGlobalReceiver(UpdateGlobalConfigC2SPacket.ID, (packet, context) -> {
            if (context.player().hasPermissionLevel(2)) {
                configManager.updateStageConfig(packet.stage(), packet.dailyLimit(), packet.maxMarks(),
                    packet.minScore(), packet.maxScore(), packet.watchRequiredTicks(), packet.parrotDailyLimit(), packet.maxActiveParrots()
                );
                for (ServerPlayerEntity player : context.player().getServer().getPlayerManager().getPlayerList()) {
                    sendGlobalConfigToPlayer(player, configManager);
                    player.sendMessage(Text.literal("§e[千里眼] 阶段 " + packet.stage() + " 配置已更新"), false);
                }
                context.player().sendMessage(Text.literal("§a阶段 " + packet.stage() + " 配置已更新并同步"), true);
            } else {
                context.player().sendMessage(Text.literal("§c我做不到"), true);
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            sendGlobalConfigToPlayer(player, configManager);
            int stage = Evil_Eyes.getPlayerStage(player, configManager);
            ServerPlayNetworking.send(player, new PlayerStageS2CPacket(stage));
        });

        // ===== 5. Camera Watch =====
        ServerTickEvents.END_SERVER_TICK.register(CameraWatchManager::tick);

        ServerPlayNetworking.registerGlobalReceiver(CameraWatchStartC2SPacket.ID, (packet, context) -> {
            ServerPlayerEntity player = context.player();
            CameraWatchManager.startWatching(player, packet.targetUuid(), player.getServer());
        });

        ServerPlayNetworking.registerGlobalReceiver(CameraWatchStopC2SPacket.ID, (packet, context) -> {
            ServerPlayerEntity player = context.player();
            MinecraftServer server = player.getServer();
            CameraWatchManager.stopWatching(player, server);
            Evil_Eyes.forceStopWatching(player, server);
        });

        // ===== 6. Module Init =====
        Evil_Eyes.initialize(configManager);
        Gazeguidance.initialize();
        ModItems.initialize();
        ModBlocks.initialize();
        Assembly_ModItems.initialize();
        mirror_of_then_and_now.initialize();
        ModBlockEntities.initialize();
        ModScreenHandlers.initialize();

        // ===== 7. Feature Event Registrations =====
        CarryEvents.register();
        ViewingModeBlocker.register();
        BodyPartManager.registerEvents();

        // ===== 8. Open Other Inventory =====
        ServerPlayNetworking.registerGlobalReceiver(OpenOtherInventoryPayload.ID, (payload, context) -> {
            ServerPlayerEntity requester = context.player();
            if (!requester.isCreative()) {
                if (!requester.getCommandTags().contains("kaibao")) {
                    requester.sendMessage(Text.literal("§c你没有§k12§r打开§k1234§r"), false);
                    return;
                }
            }
            ServerWorld world = (ServerWorld) requester.getWorld();
            Entity target = world.getEntityById(payload.targetEntityId());
            if (!(target instanceof ServerPlayerEntity targetPlayer)) {
                requester.sendMessage(Text.literal("§c目标玩家不存在"), false);
                return;
            }
            if (requester.distanceTo(targetPlayer) > 3.0) {
                requester.sendMessage(Text.literal("§c太远了，也许我该离近点？"), false);
                return;
            }
            if (targetPlayer.isDead() || !targetPlayer.isAlive()) {
                requester.sendMessage(Text.literal("§c目标玩家已死亡"), false);
                return;
            }
            VIEWING_MAP.put(requester, targetPlayer);
            requester.openHandledScreen(new OtherPlayerInventoryScreenHandlerFactory(targetPlayer));
        });

        Registry.register(Registries.SCREEN_HANDLER, Identifier.of(MOD_ID, "other_inventory"), OTHER_INVENTORY_HANDLER);

        // ===== 9. Anchor Destroy =====
        ServerPlayNetworking.registerGlobalReceiver(AnchorDestroyC2SPacket.ID, (packet, context) -> {
            ServerPlayerEntity player = context.player();
            UUID standId = packet.standId();
            World world = player.getWorld();
            Entity stand = world.getEntity(standId);
            if (stand instanceof ArmorStandEntity armorStand) {
                UUID ownerUuid = Evil_Eyes.armorStandOwner.get(standId);
                Evil_Eyes.sendExplosionToNearbyPlayers(stand, player.getServer());
                stand.remove(Entity.RemovalReason.DISCARDED);
                Evil_Eyes.armorStandOwner.remove(standId);
                Evil_Eyes.armorStandSpawnTick.remove(standId);
                if (ownerUuid != null) {
                    Evil_Eyes.configManager.removeActiveParrot(ownerUuid);
                }
                player.sendMessage(Text.literal("§a锚点已破坏"), true);
            } else {
                player.sendMessage(Text.literal("§c锚点不存在"), true);
            }
        });

        // ===== 10. Monvhua effect tick loop =====
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;
            if (tickCounter < 20) return;
            tickCounter = 0;

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                UUID uuid = player.getUuid();
                WitchRole role = WitchRole.fromPlayer(player);

                // Process pending tainted messages first
                processPendingTainted(player, uuid);

                // Floating flight ability
                boolean canFloat = player.getCommandTags().contains("Floating")
                        && player.getCommandTags().contains("MonvhuaFull");
                if (canFloat && !floatingPlayers.contains(uuid)) {
                    player.getAbilities().allowFlying = true;
                    player.sendAbilitiesUpdate();
                    floatingPlayers.add(uuid);
                    player.sendMessage(
                            Text.literal("您已获得飞行能力，尽情杀戮吧！")
                                    .formatted(Formatting.DARK_RED)
                    );
                } else if (!canFloat && floatingPlayers.remove(uuid)) {
                    player.getAbilities().allowFlying = false;
                    player.getAbilities().flying = false;
                    player.sendAbilitiesUpdate();
                    player.fallDistance = 0;
                }

                // Diagnostic: log first tick to help troubleshoot
                if (tickCounter == 0 && !DEBUG_LOGGED) {
                    LOGGER.info("[{}] Tick check: player={}, tags={}, role={}",
                            MOD_ID, player.getName().getString(),
                            player.getCommandTags(), role);
                    DEBUG_LOGGED = true;
                }

                if (role == null) {
                    RegistryEntry<StatusEffect> prev = lastEffect.remove(uuid);
                    if (prev != null) player.removeStatusEffect(prev);
                    cancelPendingTainted(uuid);
                    continue;
                }

                Scoreboard scoreboard = player.getScoreboard();
                ScoreboardObjective objective = scoreboard.getNullableObjective(OBJECTIVE_NAME);
                if (objective == null) {
                    if (!DEBUG_LOGGED)
                        LOGGER.warn("[{}] Objective '{}' not found on scoreboard!", MOD_ID, OBJECTIVE_NAME);
                    continue;
                }

                var info = scoreboard.getScore(player, objective);
                int value = info == null ? 0 : info.getScore();
                WitchStage stage = WitchStage.fromScore(value);
                if (stage == WitchStage.PROTO_WITCH
                        && player.getCommandTags().contains("MonvhuaFull")) {
                    stage = WitchStage.WITCH;
                }
                RegistryEntry<StatusEffect> desired = EFFECTS.get(role).get(stage);

                RegistryEntry<StatusEffect> previous = lastEffect.get(uuid);
                if (previous == desired) continue;

                // Stage/role changed: cancel any pending tainted messages
                cancelPendingTainted(uuid);
                lastEffect.put(uuid, desired);
                if (previous != null) player.removeStatusEffect(previous);
                player.addStatusEffect(new StatusEffectInstance(
                        desired,
                        -1, // infinite duration
                        0, false, false, true
                ));

                // Chat messages
                String fullName = "魔女化阶段——" + stage.displayName;
                if (previous != null) {
                    player.sendMessage(
                            Text.literal("【阶段变化】").formatted(Formatting.GRAY)
                                    .append(Text.literal(fullName).formatted(stage.chatColor))
                    );
                }
                player.sendMessage(
                        Text.literal("◆ " + fullName).formatted(stage.chatColor, Formatting.BOLD)
                );

                // Stage description
                if (stage == WitchStage.TAINTED) {
                    List<String> variants = new ArrayList<>(role.taintedVariants);
                    Collections.shuffle(variants, RANDOM);
                    String description = variants.get(0);
                    player.sendMessage(
                            Text.literal(description).formatted(Formatting.GRAY)
                    );
                    if (variants.size() >= 3) {
                        pendingTainted.put(uuid, new ArrayList<>(variants.subList(1, 3)));
                        pendingTaintedDelay.put(uuid, 90 + RANDOM.nextInt(61));
                    }
                } else {
                    String description = role.getDialogue(stage);
                    Formatting descColor = stage.threshold >= 60 ? Formatting.DARK_RED : Formatting.GRAY;
                    player.sendMessage(
                            Text.literal(description).formatted(descColor)
                    );
                }
            }
        });

        // ===== 11. Disconnect Cleanup =====
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            UUID uuid = player.getUuid();
            lastEffect.remove(uuid);
            cancelPendingTainted(uuid);
            floatingPlayers.remove(uuid);
            CameraWatchManager.stopWatching(player, server);
            Evil_Eyes.forceStopWatching(player, server);
            Evil_Eyes.clearPlayerMarks(uuid);
            VIEWING_MAP.remove(player);
            VIEWING_MAP.values().removeIf(v -> v == player);
            VIEW_MODE_PREFERENCE.remove(uuid);
        });

        // ===== 12. Death Cleanup =====
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayerEntity player) {
                UUID uuid = player.getUuid();
                lastEffect.remove(uuid);
                cancelPendingTainted(uuid);
                floatingPlayers.remove(uuid);
            }
        });

        // ===== 13. Fall damage prevention for floating players =====
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity player
                    && floatingPlayers.contains(player.getUuid())
                    && source.isOf(DamageTypes.FALL)) {
                player.fallDistance = 0;
                return false;
            }
            return true;
        });
    }

    private static void processPendingTainted(ServerPlayerEntity player, UUID uuid) {
        Integer delay = pendingTaintedDelay.get(uuid);
        if (delay == null) return;

        if (delay > 0) {
            pendingTaintedDelay.put(uuid, delay - 1);
            return;
        }

        List<String> queue = pendingTainted.get(uuid);
        if (queue != null && !queue.isEmpty()) {
            String msg = queue.remove(0);
            player.sendMessage(
                    Text.literal(msg).formatted(Formatting.GRAY)
            );
        }

        if (queue == null || queue.isEmpty()) {
            cancelPendingTainted(uuid);
        } else {
            pendingTaintedDelay.put(uuid, 90 + RANDOM.nextInt(61));
        }
    }

    private static void cancelPendingTainted(UUID uuid) {
        pendingTainted.remove(uuid);
        pendingTaintedDelay.remove(uuid);
    }

    private static void sendGlobalConfigToPlayer(ServerPlayerEntity player, GlobalConfigManager configManager) {
        JsonObject root = new JsonObject();
        for (int i = 1; i <= 7; i++) {
            var cfg = configManager.getStageConfig(i);
            JsonObject stageObj = new JsonObject();
            stageObj.addProperty("dailyLimit", cfg.dailyLimit());
            stageObj.addProperty("maxMarks", cfg.maxMarks());
            stageObj.addProperty("minScore", cfg.minScore());
            stageObj.addProperty("maxScore", cfg.maxScore());
            stageObj.addProperty("watchRequiredTicks", cfg.watchRequiredTicks());
            stageObj.addProperty("parrotDailyLimit", cfg.parrotDailyLimit());
            stageObj.addProperty("maxActiveParrots", cfg.maxActiveParrots());
            root.add("stage" + i, stageObj);
        }
        String json = root.toString();
        ServerPlayNetworking.send(player, new GlobalConfigS2CPacket(json));
    }
}
