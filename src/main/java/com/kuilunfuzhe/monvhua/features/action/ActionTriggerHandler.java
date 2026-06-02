package com.kuilunfuzhe.monvhua.features.action;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;

import java.util.LinkedHashMap;
import java.util.Map;

public class ActionTriggerHandler {

    public static void register() {

        // CHAT_KEYWORD trigger
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            if (sender == null) return;
            String text = message.getContent().getString();
            Map<String, Object> ctx = new LinkedHashMap<>();
            ctx.put("message", text);
            ActionEngine.fireTriggersFor(ActionEngine.TriggerType.CHAT_KEYWORD, sender, ctx);
        });

        // ATTACK trigger
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (player instanceof ServerPlayerEntity sp) {
                Map<String, Object> ctx = new LinkedHashMap<>();
                ctx.put("target", entity.getName().getString());
                ctx.put("targetUUID", entity.getUuid().toString());
                ctx.put("targetType", entity.getType().getName().getString());
                ActionEngine.fireTriggersFor(ActionEngine.TriggerType.ATTACK, sp, ctx);
            }
            return ActionResult.PASS;
        });

        // PLAYER_DEATH trigger
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayerEntity sp) {
                Map<String, Object> ctx = new LinkedHashMap<>();
                ctx.put("cause", source.getName());
                Entity attacker = source.getAttacker();
                if (attacker != null) {
                    ctx.put("killer", attacker.getName().getString());
                    ctx.put("killerUUID", attacker.getUuid().toString());
                }
                ActionEngine.fireTriggersFor(ActionEngine.TriggerType.PLAYER_DEATH, sp, ctx);
            }
        });
    }
}
