package com.kuilunfuzhe.monvhua.command;

import com.kuilunfuzhe.monvhua.features.action.ActionConfig;
import com.kuilunfuzhe.monvhua.features.action.ActionEngine;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.stream.Collectors;

public class ActionCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess registryAccess,
                                CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal("monvhua-action|动作")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("reload|重载")
                        .executes(ctx -> {
                            ActionEngine.reloadConfig();
                            ctx.getSource().sendMessage(Text.literal("§a动作配置已重载"));
                            return 1;
                        }))
                .then(CommandManager.literal("list|列表")
                        .executes(ctx -> {
                            ActionConfig cfg = ActionEngine.getConfig();
                            if (cfg == null || cfg.actions.isEmpty()) {
                                ctx.getSource().sendMessage(Text.literal("§e没有已注册的动作"));
                                return 1;
                            }
                            ctx.getSource().sendMessage(Text.literal("§6===== 动作列表 ====="));
                            for (ActionConfig.ActionDef def : cfg.actions) {
                                String status = def.enabled ? "§a[启用]" : "§c[禁用]";
                                ctx.getSource().sendMessage(Text.literal(
                                        status + " §e" + def.id + " §7- " + def.name + " §8[" + def.actionType + "]"));
                            }
                            return 1;
                        }))
                .then(CommandManager.literal("trigger|触发")
                        .then(CommandManager.argument("id", StringArgumentType.string())
                                .suggests((ctx, builder) -> {
                                    ActionConfig cfg = ActionEngine.getConfig();
                                    if (cfg != null) {
                                        return CommandSource.suggestMatching(
                                                cfg.getEnabled().stream().map(a -> a.id).collect(Collectors.toList()),
                                                builder);
                                    }
                                    return builder.buildFuture();
                                })
                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                        .executes(ctx -> {
                                            String id = StringArgumentType.getString(ctx, "id");
                                            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                                            ActionEngine.triggerManually(id, target);
                                            ctx.getSource().sendMessage(
                                                    Text.literal("§a对 " + target.getName().getString() + " 触发动作: " + id));
                                            return 1;
                                        }))
                                .executes(ctx -> {
                                    String id = StringArgumentType.getString(ctx, "id");
                                    ServerPlayerEntity self = ctx.getSource().getPlayer();
                                    if (self == null) {
                                        ctx.getSource().sendMessage(Text.literal("§c此命令需要指定目标玩家"));
                                        return 0;
                                    }
                                    ActionEngine.triggerManually(id, self);
                                    ctx.getSource().sendMessage(Text.literal("§a触发动作: " + id));
                                    return 1;
                                })))
                .then(CommandManager.literal("enable|启用")
                        .then(CommandManager.argument("id", StringArgumentType.string())
                                .suggests((ctx, builder) -> {
                                    ActionConfig cfg = ActionEngine.getConfig();
                                    if (cfg != null) {
                                        return CommandSource.suggestMatching(
                                                cfg.actions.stream().map(a -> a.id).collect(Collectors.toList()),
                                                builder);
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    String id = StringArgumentType.getString(ctx, "id");
                                    ActionConfig cfg = ActionEngine.getConfig();
                                    if (cfg != null && cfg.setEnabled(id, true)) {
                                        ctx.getSource().sendMessage(Text.literal("§a已启用动作: " + id));
                                    } else {
                                        ctx.getSource().sendMessage(Text.literal("§c动作不存在: " + id));
                                    }
                                    return 1;
                                })))
                .then(CommandManager.literal("disable|禁用")
                        .then(CommandManager.argument("id", StringArgumentType.string())
                                .suggests((ctx, builder) -> {
                                    ActionConfig cfg = ActionEngine.getConfig();
                                    if (cfg != null) {
                                        return CommandSource.suggestMatching(
                                                cfg.actions.stream().map(a -> a.id).collect(Collectors.toList()),
                                                builder);
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    String id = StringArgumentType.getString(ctx, "id");
                                    ActionConfig cfg = ActionEngine.getConfig();
                                    if (cfg != null && cfg.setEnabled(id, false)) {
                                        ctx.getSource().sendMessage(Text.literal("§a已禁用动作: " + id));
                                    } else {
                                        ctx.getSource().sendMessage(Text.literal("§c动作不存在: " + id));
                                    }
                                    return 1;
                                })))
        );
    }
}
