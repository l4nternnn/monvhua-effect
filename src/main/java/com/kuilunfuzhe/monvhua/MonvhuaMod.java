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

    public static final EnumMap<WitchStage, Holder<MobEffect>> EFFECTS = new EnumMap<>(WitchStage.class);
    private static final Map<UUID, WitchStage> lastStage = new HashMap<>();

    private int tickCounter = 0;

    @Override
    public void onInitialize() {
        for (WitchStage stage : WitchStage.values()) {
            Holder<MobEffect> holder = Registry.registerForHolder(
                    BuiltInRegistries.MOB_EFFECT,
                    ResourceLocation.fromNamespaceAndPath(MOD_ID, stage.id),
                    new DisplayOnlyEffect(stage.category, stage.color)
            );
            EFFECTS.put(stage, holder);
        }
        LOGGER.info("[{}] Registered {} witch stage effects.", MOD_ID, EFFECTS.size());

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;
            if (tickCounter < 20) return;
            tickCounter = 0;

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                Scoreboard scoreboard = player.level().getScoreboard();
                Objective objective = scoreboard.getObjective(OBJECTIVE_NAME);
                if (objective == null) continue;

                ReadOnlyScoreInfo info = scoreboard.getPlayerScoreInfo(player, objective);
                int value = info == null ? 0 : info.value();
                WitchStage current = WitchStage.fromScore(value);

                UUID uuid = player.getUUID();
                WitchStage previous = lastStage.get(uuid);
                if (previous == current) continue;

                lastStage.put(uuid, current);

                for (Holder<MobEffect> holder : EFFECTS.values()) {
                    player.removeEffect(holder);
                }
                player.addEffect(new MobEffectInstance(
                        EFFECTS.get(current),
                        MobEffectInstance.INFINITE_DURATION,
                        0, false, false, true
                ));

                if (previous != null) {
                    player.sendSystemMessage(
                            Component.literal("【阶段变化】").withStyle(ChatFormatting.GRAY)
                                    .append(Component.literal(current.displayName).withStyle(current.chatColor))
                    );
                }
                player.sendSystemMessage(
                        Component.literal("◆ " + current.displayName).withStyle(current.chatColor, ChatFormatting.BOLD)
                );
                player.sendSystemMessage(
                        Component.literal(current.description).withStyle(ChatFormatting.GRAY)
                );
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                lastStage.remove(handler.getPlayer().getUUID())
        );

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) {
                lastStage.remove(player.getUUID());
            }
        });
    }
}
