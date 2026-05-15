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
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.Scoreboard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MonvhuaMod implements ModInitializer {
    public static final String MOD_ID = "monvhua";
    public static final String OBJECTIVE_NAME = "monvhua";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final EnumMap<WitchRole, EnumMap<WitchStage, Holder<MobEffect>>> EFFECTS =
            new EnumMap<>(WitchRole.class);
    private static final Map<UUID, Holder<MobEffect>> lastEffect = new HashMap<>();

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

                if (role == null) {
                    Holder<MobEffect> prev = lastEffect.remove(uuid);
                    if (prev != null) player.removeEffect(prev);
                    continue;
                }

                Scoreboard scoreboard = player.level().getScoreboard();
                Objective objective = scoreboard.getObjective(OBJECTIVE_NAME);
                if (objective == null) continue;

                ReadOnlyScoreInfo info = scoreboard.getPlayerScoreInfo(player, objective);
                int value = info == null ? 0 : info.value();
                WitchStage stage = WitchStage.fromScore(value);
                Holder<MobEffect> desired = EFFECTS.get(role).get(stage);

                Holder<MobEffect> previous = lastEffect.get(uuid);
                if (previous == desired) continue;

                lastEffect.put(uuid, desired);
                if (previous != null) player.removeEffect(previous);
                player.addEffect(new MobEffectInstance(
                        desired,
                        MobEffectInstance.INFINITE_DURATION,
                        0, false, false, true
                ));

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
                player.sendSystemMessage(
                        Component.literal(stage.description).withStyle(ChatFormatting.GRAY)
                );
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                lastEffect.remove(handler.getPlayer().getUUID())
        );

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) {
                lastEffect.remove(player.getUUID());
            }
        });
    }
}
