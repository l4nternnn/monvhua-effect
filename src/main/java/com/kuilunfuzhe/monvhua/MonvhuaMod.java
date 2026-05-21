package com.kuilunfuzhe.monvhua;

import com.kuilunfuzhe.monvhua.effect.DisplayOnlyEffect;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.Scoreboard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class MonvhuaMod implements ModInitializer {
    public static final String MOD_ID = "monvhua";
    public static final String OBJECTIVE_NAME = "monvhua";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final EnumMap<WitchRole, EnumMap<WitchStage, Holder<MobEffect>>> EFFECTS =
            new EnumMap<>(WitchRole.class);
    private static final Map<UUID, Holder<MobEffect>> lastEffect = new HashMap<>();

    // Tainted stage random scheduling
    private static final Random RANDOM = new Random();
    private static final Map<UUID, List<String>> pendingTainted = new HashMap<>();
    private static final Map<UUID, Integer> pendingTaintedDelay = new HashMap<>();

    // Floating flight ability tracking
    private static final Set<UUID> floatingPlayers = new HashSet<>();

    private int tickCounter = 0;

    @Override
    public void onInitialize() {
        int count = 0;
        for (WitchRole role : WitchRole.values()) {
            EnumMap<WitchStage, Holder<MobEffect>> roleMap = new EnumMap<>(WitchStage.class);
            for (WitchStage stage : WitchStage.values()) {
                String effectId = role.id + "_" + stage.id;
                Holder<MobEffect> holder = Registry.registerForHolder(
                        BuiltInRegistries.MOB_EFFECT,
                        ResourceLocation.fromNamespaceAndPath(MOD_ID, effectId),
                        new DisplayOnlyEffect(stage.category, stage.color)
                );
                roleMap.put(stage, holder);
                count++;
            }
            EFFECTS.put(role, roleMap);
        }
        LOGGER.info("[{}] Registered {} role x stage effects.", MOD_ID, count);

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;
            if (tickCounter < 20) return;
            tickCounter = 0;

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                UUID uuid = player.getUUID();
                WitchRole role = WitchRole.fromPlayer(player);

                // Process pending tainted messages first
                processPendingTainted(player, uuid);

                // Floating flight ability
                boolean canFloat = player.getTags().contains("Floating")
                        && player.getTags().contains("MonvhuaFull");
                if (canFloat && !floatingPlayers.contains(uuid)) {
                    player.getAbilities().mayfly = true;
                    floatingPlayers.add(uuid);
                    player.displayClientMessage(
                            Component.literal("您已获得飞行能力，尽情杀戮吧！")
                                    .withStyle(ChatFormatting.DARK_RED),
                            true
                    );
                } else if (!canFloat && floatingPlayers.remove(uuid)) {
                    player.getAbilities().mayfly = false;
                    player.getAbilities().flying = false;
                    player.fallDistance = 0;
                }

                if (role == null) {
                    Holder<MobEffect> prev = lastEffect.remove(uuid);
                    if (prev != null) player.removeEffect(prev);
                    cancelPendingTainted(uuid);
                    continue;
                }

                Scoreboard scoreboard = player.level().getScoreboard();
                Objective objective = scoreboard.getObjective(OBJECTIVE_NAME);
                if (objective == null) continue;

                ReadOnlyScoreInfo info = scoreboard.getPlayerScoreInfo(player, objective);
                int value = info == null ? 0 : info.value();
                WitchStage stage = WitchStage.fromScore(value);
                if (stage == WitchStage.PROTO_WITCH
                        && player.getTags().contains("MonvhuaFull")) {
                    stage = WitchStage.WITCH;
                }
                Holder<MobEffect> desired = EFFECTS.get(role).get(stage);

                Holder<MobEffect> previous = lastEffect.get(uuid);
                if (previous == desired) continue;

                // Stage/role changed: cancel any pending tainted messages
                cancelPendingTainted(uuid);
                lastEffect.put(uuid, desired);
                if (previous != null) player.removeEffect(previous);
                player.addEffect(new MobEffectInstance(
                        desired,
                        MobEffectInstance.INFINITE_DURATION,
                        0, false, false, true
                ));

                // Chat messages
                String fullName = "魔女化阶段——" + stage.displayName;
                if (previous != null) {
                    player.sendSystemMessage(
                            Component.literal("【阶段变化】").withStyle(ChatFormatting.GRAY)
                                    .append(Component.literal(fullName).withStyle(stage.chatColor))
                    );
                }
                player.sendSystemMessage(
                        Component.literal("◆ " + fullName).withStyle(stage.chatColor, ChatFormatting.BOLD)
                );

                // Stage description
                if (stage == WitchStage.TAINTED) {
                    // Shuffle 3 variants, send first as description, queue remaining 2
                    List<String> variants = new ArrayList<>(role.taintedVariants);
                    Collections.shuffle(variants, RANDOM);
                    String description = variants.get(0);
                    player.sendSystemMessage(
                            Component.literal(description).withStyle(ChatFormatting.GRAY)
                    );
                    if (variants.size() >= 3) {
                        pendingTainted.put(uuid, new ArrayList<>(variants.subList(1, 3)));
                        pendingTaintedDelay.put(uuid, 90 + RANDOM.nextInt(61));
                    }
                } else {
                    String description = role.getDialogue(stage);
                    ChatFormatting descColor = stage.threshold >= 60 ? ChatFormatting.DARK_RED : ChatFormatting.GRAY;
                    player.sendSystemMessage(
                            Component.literal(description).withStyle(descColor)
                    );
                }
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID uuid = handler.getPlayer().getUUID();
            lastEffect.remove(uuid);
            cancelPendingTainted(uuid);
            floatingPlayers.remove(uuid);
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) {
                UUID uuid = player.getUUID();
                lastEffect.remove(uuid);
                cancelPendingTainted(uuid);
                floatingPlayers.remove(uuid);
            }
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer player
                    && floatingPlayers.contains(player.getUUID())
                    && source.typeHolder().is(DamageTypes.FALL)) {
                player.fallDistance = 0;
                return false;
            }
            return true;
        });
    }

    private static void processPendingTainted(ServerPlayer player, UUID uuid) {
        Integer delay = pendingTaintedDelay.get(uuid);
        if (delay == null) return;

        if (delay > 0) {
            pendingTaintedDelay.put(uuid, delay - 1);
            return;
        }

        List<String> queue = pendingTainted.get(uuid);
        if (queue != null && !queue.isEmpty()) {
            String msg = queue.remove(0);
            player.sendSystemMessage(
                    Component.literal(msg).withStyle(ChatFormatting.GRAY)
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
}
