package com.kuilunfuzhe.monvhua.command;

import com.kuilunfuzhe.monvhua.features.gravity.GravityMagic;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public final class GravityCommand {
    private GravityCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                net.minecraft.registry.RegistryWrapper.WrapperLookup registryAccess,
                                CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(gravityRoot("monvhua-gravity_重力"));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> gravityRoot(String name) {
        return CommandManager.literal(name)
                .requires(source -> source.hasPermissionLevel(2))
                .then(logicCommand("logic_逻辑"))
                .then(areaCommand("area_区域"));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> logicCommand(String name) {
        return CommandManager.literal(name)
                .then(CommandManager.literal("force_强制")
                        .executes(context -> setLogic(context, GravityMagic.LogicMode.FORCE)))
                .then(CommandManager.literal("surface_表面")
                        .executes(context -> setLogic(context, GravityMagic.LogicMode.SURFACE)))
                .then(CommandManager.literal("toggle_切换")
                        .executes(GravityCommand::toggleLogic))
                .then(CommandManager.literal("show_显示")
                        .executes(GravityCommand::showLogic));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> areaCommand(String name) {
        return CommandManager.literal(name)
                .then(createAreaCommand("create_创建"))
                .then(clearAreaCommand("clear_清除"));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> createAreaCommand(String name) {
        return CommandManager.literal(name)
                .then(CommandManager.argument("radius", IntegerArgumentType.integer(1, 256))
                        .then(CommandManager.argument("time", StringArgumentType.word())
                                .executes(context -> createArea(context, GravityMagic.DEFAULT_AREA_HEIGHT)))
                        .then(CommandManager.argument("height", IntegerArgumentType.integer(1, 256))
                                .then(CommandManager.argument("time", StringArgumentType.word())
                                        .executes(context -> createArea(context, IntegerArgumentType.getInteger(context, "height"))))));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> clearAreaCommand(String name) {
        return CommandManager.literal(name)
                .then(CommandManager.literal("nearest_最近")
                        .executes(GravityCommand::clearNearest))
                .then(CommandManager.literal("all_全部")
                        .executes(GravityCommand::clearAll));
    }

    private static int setLogic(CommandContext<ServerCommandSource> context, GravityMagic.LogicMode mode) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendError(Text.literal("此命令只能由玩家执行。"));
            return 0;
        }

        int applied = setLogicForAllOnline(context, mode);
        context.getSource().sendFeedback(() -> Text.literal("§a[重力] 已将所有在线玩家逻辑模式设为: " + logicName(mode) + " (" + applied + "人)"), true);
        return 1;
    }

    private static int toggleLogic(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendError(Text.literal("此命令只能由玩家执行。"));
            return 0;
        }

        GravityMagic.LogicMode next = GravityMagic.getLogicMode(player) == GravityMagic.LogicMode.SURFACE
                ? GravityMagic.LogicMode.FORCE
                : GravityMagic.LogicMode.SURFACE;
        int applied = setLogicForAllOnline(context, next);
        context.getSource().sendFeedback(() -> Text.literal("§a[重力] 已将所有在线玩家逻辑模式切换为: " + logicName(next) + " (" + applied + "人)"), true);
        return 1;
    }

    private static int setLogicForAllOnline(CommandContext<ServerCommandSource> context, GravityMagic.LogicMode mode) {
        int applied = 0;
        for (ServerPlayerEntity target : context.getSource().getServer().getPlayerManager().getPlayerList()) {
            GravityMagic.LogicMode next = GravityMagic.setLogicMode(target, mode);
            if (next == mode) {
                applied++;
            }
        }
        return applied;
    }

    private static int showLogic(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendError(Text.literal("此命令只能由玩家执行。"));
            return 0;
        }

        GravityMagic.LogicMode mode = GravityMagic.getLogicMode(player);
        context.getSource().sendFeedback(() -> Text.literal("§b[重力] 逻辑模式: " + logicName(mode)), false);
        return 1;
    }

    private static String logicName(GravityMagic.LogicMode mode) {
        return mode == GravityMagic.LogicMode.SURFACE ? "表面" : "强制";
    }

    private static int createArea(CommandContext<ServerCommandSource> context, int height) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendError(Text.literal("此命令只能由玩家执行。"));
            return 0;
        }

        int radius = IntegerArgumentType.getInteger(context, "radius");
        String time = StringArgumentType.getString(context, "time");
        int ticks = parseTicks(time);
        if (ticks == 0) {
            context.getSource().sendError(Text.literal("时间必须是秒数或“无限”。"));
            return 0;
        }

        ServerWorld world = (ServerWorld) player.getWorld();
        BlockPos center = player.getBlockPos();
        double gravity = GravityMagic.getSelectedGravity(player);
        GravityMagic.addAreaGravity(world, center, radius, height, ticks, gravity);

        String duration = ticks == GravityMagic.INFINITE_AREA_TICKS ? "无限" : ticks / 20 + "秒";
        context.getSource().sendFeedback(
                () -> Text.literal("\u00a7a[重力] 已创建反转区域: "
                        + center.toShortString() + " 半径=" + radius + " 高度=" + height + " 时间=" + duration),
                true
        );
        return 1;
    }

    private static int clearNearest(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendError(Text.literal("此命令只能由玩家执行。"));
            return 0;
        }

        int removed = GravityMagic.clearNearestAreaGravity((ServerWorld) player.getWorld(), player.getPos());
        if (removed == 0) {
            context.getSource().sendError(Text.literal("当前维度没有可清除的反转区域。"));
            return 0;
        }

        context.getSource().sendFeedback(() -> Text.literal("\u00a7a[重力] 已清除最近的反转区域。"), true);
        return removed;
    }

    private static int clearAll(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendError(Text.literal("此命令只能由玩家执行。"));
            return 0;
        }

        int removed = GravityMagic.clearAllAreaGravity((ServerWorld) player.getWorld());
        context.getSource().sendFeedback(
                () -> Text.literal("\u00a7a[重力] 已清除当前维度 " + removed + " 个反转区域。"),
                true
        );
        return removed;
    }

    private static int parseTicks(String value) {
        if ("wuxian".equalsIgnoreCase(value) || "无限".equals(value)) {
            return GravityMagic.INFINITE_AREA_TICKS;
        }

        try {
            double seconds = Double.parseDouble(value);
            if (seconds <= 0.0D) {
                return 0;
            }
            return Math.max(1, (int) Math.round(seconds * 20.0D));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
