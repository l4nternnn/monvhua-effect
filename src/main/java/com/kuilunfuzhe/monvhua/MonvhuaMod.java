package com.kuilunfuzhe.monvhua;

import com.kuilunfuzhe.monvhua.effect.DisplayOnlyEffect;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardScore;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class MonvhuaMod implements ModInitializer {
    public static final String MOD_ID = "monvhua";
    public static final String OBJECTIVE_NAME = "monvhua";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final EnumMap<WitchRole, EnumMap<WitchStage, RegistryEntry<StatusEffect>>> EFFECTS =
            new EnumMap<>(WitchRole.class);
    private static final Map<UUID, RegistryEntry<StatusEffect>> lastEffect = new HashMap<>();

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
                boolean canFloat = player.getTags().contains("Floating")
                        && player.getTags().contains("MonvhuaFull");
                if (canFloat && !floatingPlayers.contains(uuid)) {
                    player.getAbilities().mayfly = true;
                    player.onUpdateAbilities();
                    floatingPlayers.add(uuid);
                    player.sendMessage(
                            Text.literal("您已获得飞行能力，尽情杀戮吧！")
                                    .formatted(Formatting.DARK_RED)
                    );
                } else if (!canFloat && floatingPlayers.remove(uuid)) {
                    player.getAbilities().mayfly = false;
                    player.getAbilities().flying = false;
                    player.onUpdateAbilities();
                    player.fallDistance = 0;
                }

                if (role == null) {
                    RegistryEntry<StatusEffect> prev = lastEffect.remove(uuid);
                    if (prev != null) player.removeStatusEffect(prev);
                    cancelPendingTainted(uuid);
                    continue;
                }

                Scoreboard scoreboard = player.getServerWorld().getScoreboard();
                ScoreboardObjective objective = scoreboard.getObjective(OBJECTIVE_NAME);
                if (objective == null) continue;

                ScoreboardScore info = scoreboard.getScore(player, objective);
                int value = info == null ? 0 : info.getScore();
                WitchStage stage = WitchStage.fromScore(value);
                if (stage == WitchStage.PROTO_WITCH
                        && player.getTags().contains("MonvhuaFull")) {
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
                        StatusEffectInstance.INFINITE_DURATION,
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
                    // Shuffle 3 variants, send first as description, queue remaining 2
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

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID uuid = handler.getPlayer().getUuid();
            lastEffect.remove(uuid);
            cancelPendingTainted(uuid);
            floatingPlayers.remove(uuid);
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayerEntity player) {
                UUID uuid = player.getUuid();
                lastEffect.remove(uuid);
                cancelPendingTainted(uuid);
                floatingPlayers.remove(uuid);
            }
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity player
                    && floatingPlayers.contains(player.getUuid())
                    && source.typeHolder().is(DamageTypes.FALL)) {
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
}
