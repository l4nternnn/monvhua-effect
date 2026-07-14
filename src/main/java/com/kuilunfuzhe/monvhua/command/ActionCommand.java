package com.kuilunfuzhe.monvhua.command;

import com.kuilunfuzhe.monvhua.features.action.ActionConfig;
import com.kuilunfuzhe.monvhua.features.action.ActionEngine;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
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
        dispatcher.register(actionRoot("monvhua-action_动作"));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> actionRoot(String name) {
        return CommandManager.literal(name)
                .requires(source -> source.hasPermissionLevel(2))
                .then(reloadCommand("reload_重载"))
                .then(listCommand("list_列表"))
                .then(triggerCommand("trigger_触发"))
                .then(setEnabledCommand("enable_启用", true))
                .then(setEnabledCommand("disable_禁用", false));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> reloadCommand(String name) {
        return CommandManager.literal(name)
                .executes(ctx -> {
                    ActionEngine.reloadConfig();
                    ctx.getSource().sendMessage(Text.literal("§a动作配置已重载"));
                    return 1;
                });
    }

    private static LiteralArgumentBuilder<ServerCommandSource> listCommand(String name) {
        return CommandManager.literal(name)
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
                });
    }

    private static LiteralArgumentBuilder<ServerCommandSource> triggerCommand(String name) {
        return CommandManager.literal(name)
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
                        }));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> setEnabledCommand(String name, boolean enabled) {
        return CommandManager.literal(name)
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
                            if (cfg != null && cfg.setEnabled(id, enabled)) {
                                ctx.getSource().sendMessage(Text.literal((enabled ? "§a已启用动作: " : "§a已禁用动作: ") + id));
                            } else {
                                ctx.getSource().sendMessage(Text.literal("§c动作不存在: " + id));
                            }
                            return 1;
                        }));
    }
}
