package com.kuilunfuzhe.monvhua.command;

import com.kuilunfuzhe.monvhua.features.gravity.GravityMagic;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
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
        dispatcher.register(CommandManager.literal("monvhua-gravity")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("logic")
                        .then(CommandManager.literal("force")
                                .executes(context -> setLogic(context, GravityMagic.LogicMode.FORCE)))
                        .then(CommandManager.literal("surface")
                                .executes(context -> setLogic(context, GravityMagic.LogicMode.SURFACE)))
                        .then(CommandManager.literal("toggle")
                                .executes(GravityCommand::toggleLogic))
                        .then(CommandManager.literal("show")
                                .executes(GravityCommand::showLogic)))
                .then(CommandManager.literal("area")
                        .then(CommandManager.literal("create")
                                .then(CommandManager.argument("radius", IntegerArgumentType.integer(1, 256))
                                        .then(CommandManager.argument("time", StringArgumentType.word())
                                                .executes(context -> createArea(context, GravityMagic.DEFAULT_AREA_HEIGHT)))
                                        .then(CommandManager.argument("height", IntegerArgumentType.integer(1, 256))
                                                .then(CommandManager.argument("time", StringArgumentType.word())
                                                        .executes(context -> createArea(context, IntegerArgumentType.getInteger(context, "height")))))))
                        .then(CommandManager.literal("clear")
                                .then(CommandManager.literal("nearest")
                                        .executes(GravityCommand::clearNearest))
                                .then(CommandManager.literal("all")
                                        .executes(GravityCommand::clearAll)))));
    }

    private static int setLogic(CommandContext<ServerCommandSource> context, GravityMagic.LogicMode mode) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendError(Text.literal("This command requires a player."));
            return 0;
        }

        GravityMagic.LogicMode next = GravityMagic.setLogicMode(player, mode);
        context.getSource().sendFeedback(() -> Text.literal("§a[Gravity] Logic mode: " + logicName(next)), true);
        return 1;
    }

    private static int toggleLogic(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendError(Text.literal("This command requires a player."));
            return 0;
        }

        GravityMagic.LogicMode next = GravityMagic.toggleLogicMode(player);
        context.getSource().sendFeedback(() -> Text.literal("§a[Gravity] Logic mode: " + logicName(next)), true);
        return 1;
    }

    private static int showLogic(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendError(Text.literal("This command requires a player."));
            return 0;
        }

        GravityMagic.LogicMode mode = GravityMagic.getLogicMode(player);
        context.getSource().sendFeedback(() -> Text.literal("§b[Gravity] Logic mode: " + logicName(mode)), false);
        return 1;
    }

    private static String logicName(GravityMagic.LogicMode mode) {
        return mode == GravityMagic.LogicMode.SURFACE ? "surface" : "force";
    }

    private static int createArea(CommandContext<ServerCommandSource> context, int height) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendError(Text.literal("This command requires a player."));
            return 0;
        }

        int radius = IntegerArgumentType.getInteger(context, "radius");
        String time = StringArgumentType.getString(context, "time");
        int ticks = parseTicks(time);
        if (ticks == 0) {
            context.getSource().sendError(Text.literal("time must be seconds or wuxian."));
            return 0;
        }

        ServerWorld world = (ServerWorld) player.getWorld();
        BlockPos center = player.getBlockPos();
        double gravity = GravityMagic.getSelectedGravity(player);
        GravityMagic.addAreaGravity(world, center, radius, height, ticks, gravity);

        String duration = ticks == GravityMagic.INFINITE_AREA_TICKS ? "wuxian" : ticks / 20 + "s";
        context.getSource().sendFeedback(
                () -> Text.literal("\u00a7a[Gravity] Created inverted area at "
                        + center.toShortString() + " r=" + radius + " h=" + height + " time=" + duration),
                true
        );
        return 1;
    }

    private static int clearNearest(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendError(Text.literal("This command requires a player."));
            return 0;
        }

        int removed = GravityMagic.clearNearestAreaGravity((ServerWorld) player.getWorld(), player.getPos());
        if (removed == 0) {
            context.getSource().sendError(Text.literal("No inverted area found in this dimension."));
            return 0;
        }

        context.getSource().sendFeedback(() -> Text.literal("\u00a7a[Gravity] Cleared nearest inverted area."), true);
        return removed;
    }

    private static int clearAll(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendError(Text.literal("This command requires a player."));
            return 0;
        }

        int removed = GravityMagic.clearAllAreaGravity((ServerWorld) player.getWorld());
        context.getSource().sendFeedback(
                () -> Text.literal("\u00a7a[Gravity] Cleared " + removed + " inverted area(s) in this dimension."),
                true
        );
        return removed;
    }

    private static int parseTicks(String value) {
        if ("wuxian".equalsIgnoreCase(value)) {
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
