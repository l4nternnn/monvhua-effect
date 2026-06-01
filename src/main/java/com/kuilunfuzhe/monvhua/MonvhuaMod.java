package com.kuilunfuzhe.monvhua;

import com.google.gson.JsonObject;
import com.kuilunfuzhe.monvhua.command.*;
import com.kuilunfuzhe.monvhua.command.mirror.MirrorCommand;
import com.kuilunfuzhe.monvhua.command.mirror.MirrorDataStore;
import com.kuilunfuzhe.monvhua.config.GlobalConfigManager;
import com.kuilunfuzhe.monvhua.effect.DisplayOnlyEffect;
import com.kuilunfuzhe.monvhua.entity.ModBlockEntities;
import com.kuilunfuzhe.monvhua.features.block.body.BodyPartManager;
import com.kuilunfuzhe.monvhua.features.carryentity.CarryEvents;
import com.kuilunfuzhe.monvhua.features.evil_eyes.Evil_Eyes;
import com.kuilunfuzhe.monvhua.features.evil_eyes.ViewingModeBlocker;
import com.kuilunfuzhe.monvhua.features.evil_eyes.server.CameraWatchManager;
import com.kuilunfuzhe.monvhua.features.guidance.Gazeguidance;
import com.kuilunfuzhe.monvhua.item.ModItemGroups;
import com.kuilunfuzhe.monvhua.item.config.GazeConfig;
import com.kuilunfuzhe.monvhua.item.config.SecrecyConfig;
import com.kuilunfuzhe.monvhua.item.gazeguidance.ModItems;
import com.kuilunfuzhe.monvhua.item.mirror.mirror_of_then_and_now;
import com.kuilunfuzhe.monvhua.item.secrecy.SecrecyItem;
import com.kuilunfuzhe.monvhua.item.modblock.ModBlocks;
import com.kuilunfuzhe.monvhua.item.modblock.moditems.Assembly_ModItems;
import com.kuilunfuzhe.monvhua.network.ModNetworking;
import com.kuilunfuzhe.monvhua.network.bodypose.PlacePosedBodyC2SPacket;
import com.kuilunfuzhe.monvhua.network.camerawatch.*;
import com.kuilunfuzhe.monvhua.network.evil_eyes.*;
import com.kuilunfuzhe.monvhua.network.gazeguidance.*;
import com.kuilunfuzhe.monvhua.item.config.MirrorConfig;
import com.kuilunfuzhe.monvhua.network.mirror.MirrorChargeC2SPacket;
import com.kuilunfuzhe.monvhua.network.mirror.MirrorConfigS2CPacket;
import com.kuilunfuzhe.monvhua.network.mirror.MirrorConfigUpdateC2SPacket;
import com.kuilunfuzhe.monvhua.network.mirror.MirrorStateS2CPacket;
import com.kuilunfuzhe.monvhua.network.mirror.MirrorToggleC2SPacket;
import com.kuilunfuzhe.monvhua.network.mirror.RequestMirrorConfigC2SPacket;
import com.kuilunfuzhe.monvhua.network.openback.CarryEntityPayload;
import com.kuilunfuzhe.monvhua.network.openback.OpenOtherInventoryPayload;
import com.kuilunfuzhe.monvhua.network.openback.PlaceCarriedEntityPayload;
import com.kuilunfuzhe.monvhua.network.secrecy.RequestSecrecyConfigC2SPacket;
import com.kuilunfuzhe.monvhua.network.secrecy.SecrecyConfigS2CPacket;
import com.kuilunfuzhe.monvhua.network.secrecy.SecrecyConfigUpdateC2SPacket;
import com.kuilunfuzhe.monvhua.network.secrecy.SecrecyStateS2CPacket;
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
import net.minecraft.component.type.ProfileComponent;
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
import com.kuilunfuzhe.monvhua.event.ServerTickHandler;

public class MonvhuaMod implements ModInitializer {
    public static final String MOD_ID = "monvhua";
    public static final String OBJECTIVE_NAME = "monvhua";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // ===== 魔女化效果系统 =====
    public static final EnumMap<WitchRole, EnumMap<WitchStage, RegistryEntry<StatusEffect>>> EFFECTS =
            new EnumMap<>(WitchRole.class);
    private static final Map<UUID, RegistryEntry<StatusEffect>> lastEffect = new HashMap<>();

    // 腐化阶段随机调度
    private static final Random RANDOM = new Random();
    private static final Map<UUID, List<String>> pendingTainted = new HashMap<>();
    private static final Map<UUID, Integer> pendingTaintedDelay = new HashMap<>();

    // 飘浮飞行能力追踪
    private static final Set<UUID> floatingPlayers = new HashSet<>();

    // ===== 漂浮魔法系统（服务端）=====
    public static final Set<UUID> floatingPlayersServer = new HashSet<>();

    // ===== 千里眼共享状态 =====
    public static ScreenHandlerType<OtherPlayerInventoryScreenHandler> OTHER_INVENTORY_HANDLER =
            new ScreenHandlerType<>(OtherPlayerInventoryScreenHandler::new, FeatureSet.empty());
    public static final Map<ServerPlayerEntity, ServerPlayerEntity> VIEWING_MAP = new ConcurrentHashMap<>();
    public static final Map<UUID, String> VIEW_MODE_PREFERENCE = new ConcurrentHashMap<>();

    private int tickCounter = 0;

    @Override
    public void onInitialize() {
        // ===== 1. 注册 112 个 DisplayOnlyEffect 实例 =====
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

        // ===== 2. 网络包注册 =====
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
        MirrorConfigS2CPacket.register();
        SecrecyConfigS2CPacket.register();
        SecrecyStateS2CPacket.register();

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
        PlacePosedBodyC2SPacket.register();
        CameraWatchStartC2SPacket.register();
        CameraWatchStopC2SPacket.register();
        MirrorToggleC2SPacket.register();
        MirrorConfigUpdateC2SPacket.register();
        RequestMirrorConfigC2SPacket.register();
        RequestSecrecyConfigC2SPacket.register();
        SecrecyConfigUpdateC2SPacket.register();

        ServerPlayNetworking.registerGlobalReceiver(MirrorToggleC2SPacket.ID, (packet, context) -> {
            MirrorCommand.toggleViewport(context.player());
        });

        ServerPlayNetworking.registerGlobalReceiver(MirrorChargeC2SPacket.ID, (packet, context) -> {
            context.server().execute(() -> {
                ServerPlayerEntity player = context.player();
                if (player.getMainHandStack().getItem() == mirror_of_then_and_now.MIRROR_ITEM) {
                    if (packet.start()) {
                        MirrorCommand.startCharging(player);
                    } else {
                        MirrorCommand.stopCharging(player);
                    }
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(MirrorConfigUpdateC2SPacket.ID, (packet, context) -> {
            context.server().execute(() -> {
                MirrorConfig newConfig = MirrorConfig.fromJson(packet.json());
                if (newConfig != null) {
                    MirrorConfig.setInstance(newConfig);
                    for (ServerPlayerEntity p : context.server().getPlayerManager().getPlayerList()) {
                        ServerPlayNetworking.send(p, new MirrorConfigS2CPacket(newConfig.toJson()));
                    }
                    if (context.player() != null) {
                        context.player().sendMessage(Text.literal("§a镜子配置已更新并同步"), true);
                    }
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(RequestMirrorConfigC2SPacket.ID, (packet, context) -> {
            MirrorConfig config = MirrorConfig.getInstance();
            ServerPlayNetworking.send(context.player(), new MirrorConfigS2CPacket(config.toJson()));
        });

        ServerPlayNetworking.registerGlobalReceiver(RequestSecrecyConfigC2SPacket.ID, (packet, context) -> {
            ServerPlayNetworking.send(context.player(), new SecrecyConfigS2CPacket(SecrecyConfig.getInstance().toJson()));
        });

        ServerPlayNetworking.registerGlobalReceiver(PlacePosedBodyC2SPacket.ID, (packet, context) -> {
            context.server().execute(() -> {
                ServerPlayerEntity player = context.player();
                if (!player.isCreative() && !player.hasPermissionLevel(2)) {
                    player.sendMessage(Text.literal("No permission to place posed body model"), true);
                    return;
                }
                if (packet.playerSkin()) {
                    ServerPlayerEntity source = context.server().getPlayerManager().getPlayer(packet.playerName());
                    if (source == null) {
                        player.sendMessage(Text.literal("Player " + packet.playerName() + " is not online"), true);
                        return;
                    }
                    BodyPartManager.createPosedCombinedDisplay(player, new ProfileComponent(source.getGameProfile()), packet.slimModel(), packet.poseValues());
                } else {
                    BodyPartManager.createPosedCombinedDisplay(player, packet.skinName(), packet.slimModel(), packet.poseValues());
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(SecrecyConfigUpdateC2SPacket.ID, (packet, context) -> {
            context.server().execute(() -> {
                if (!context.player().hasPermissionLevel(2) && !context.player().isCreative()) {
                    context.player().sendMessage(Text.literal("§c你没有权限修改窃密配置"), true);
                    return;
                }
                SecrecyConfig newConfig = SecrecyConfig.fromJson(packet.json());
                SecrecyConfig.setInstance(newConfig);
                for (int stage = 1; stage <= newConfig.stages.length; stage++) {
                    int range = newConfig.getRange(stage);
                    double probability = newConfig.getProbability(stage);
                    String command = "mindreading " + stage + " " + range + " " + probability;
                    context.server().getCommandManager().executeWithPrefix(context.server().getCommandSource().withLevel(4), command);
                }
                for (ServerPlayerEntity p : context.server().getPlayerManager().getPlayerList()) {
                    ServerPlayNetworking.send(p, new SecrecyConfigS2CPacket(newConfig.toJson()));
                }
                context.player().sendMessage(Text.literal("§a窃密配置已更新，并已同步 mindreading 范围/概率"), true);
            });
        });

        // ===== 3. 命令注册 =====
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

        // ===== 4. 配置系统 =====
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
            MirrorCommand.syncToClient(player);
            ServerPlayNetworking.send(player, new SecrecyConfigS2CPacket(SecrecyConfig.getInstance().toJson()));
        });

        // ===== 5. 持久化加载 =====
        MirrorDataStore.load();

        // ===== 6. 摄像机追踪 & 镜面视口 tick =====
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            CameraWatchManager.tick(server);
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                MirrorCommand.tickViewports(player);
                MirrorCommand.tickCharging(player);
            }
        });

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

        // ===== 7. 模块初始化 =====
        Evil_Eyes.initialize(configManager);
        Gazeguidance.initialize();
        ModItems.initialize();
        SecrecyItem.initialize(configManager);
        ModBlocks.initialize();
        Assembly_ModItems.initialize();
        mirror_of_then_and_now.initialize();
        ModBlockEntities.initialize();
        ModScreenHandlers.initialize();
        ModItemGroups.initialize();


        // ===== 8. 功能事件注册 =====
        CarryEvents.register();
        ViewingModeBlocker.register();
        BodyPartManager.registerEvents();

        // ===== 9. 打开他人背包 =====
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

        // ===== 10. 锚点破坏 =====
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

        // ===== 11. 魔女化效果 tick 循环 =====
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;
            if (tickCounter < 20) return;
            tickCounter = 0;

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                UUID uuid = player.getUuid();
                WitchRole role = WitchRole.fromPlayer(player);

                // 先处理待发送的腐化消息
                processPendingTainted(player, uuid);

                // 飘浮飞行能力
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

                if (role == null) {
                    RegistryEntry<StatusEffect> prev = lastEffect.remove(uuid);
                    if (prev != null) player.removeStatusEffect(prev);
                    cancelPendingTainted(uuid);
                    continue;
                }

                Scoreboard scoreboard = player.getScoreboard();
                ScoreboardObjective objective = scoreboard.getNullableObjective(OBJECTIVE_NAME);
                if (objective == null) continue;

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

                // 阶段/角色变化：取消所有待发送的腐化消息
                cancelPendingTainted(uuid);
                lastEffect.put(uuid, desired);
                if (previous != null) player.removeStatusEffect(previous);
                player.addStatusEffect(new StatusEffectInstance(
                        desired,
                        -1, // 无限持续时间
                        0, false, false, true
                ));

                // 聊天消息
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

                // 阶段描述
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

        // ===== 12. 断线清理 =====
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
            MirrorCommand.cleanup(uuid);
            SecrecyItem.exitSecrecy(player);
        });

        // ===== 13. 死亡清理 =====
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayerEntity player) {
                UUID uuid = player.getUuid();
                lastEffect.remove(uuid);
                cancelPendingTainted(uuid);
                floatingPlayers.remove(uuid);
                MirrorCommand.cleanup(uuid);
            SecrecyItem.exitSecrecy(player);
            }
        });

        // ===== 14. 飘浮玩家免疫摔落伤害 =====
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity player
                    && floatingPlayers.contains(player.getUuid())
                    && source.isOf(DamageTypes.FALL)) {
                player.fallDistance = 0;
                return false;
            }
            return true;
        });
        // 注册漂浮魔法能量系统
        ServerTickHandler.register();
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
