package com.kuilunfuzhe.monvhua.features.glitch;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public final class GlitchPlaneCommand {
       private static ServerWorld world(ServerCommandSource source) { return source.getWorld(); }
    private static int create(ServerCommandSource source, String name, BlockPos from, BlockPos to) { var p = GlitchPlaneManager.create(world(source), name, from, to); if (p.isEmpty()) { source.sendError(Text.literal("Invalid glitch plane: use a unique [a-zA-Z0-9_-] name, one fixed axis, non-zero sides, and <= 64x64.")); return 0; } GlitchPlaneManager.broadcast(world(source)); source.sendFeedback(() -> Text.literal("Created glitch plane " + name), true); return 1; }
    private static int setEnabled(ServerCommandSource s, String n, boolean enabled) { return update(s,n,p -> p.withEnabled(enabled), enabled ? "Started " : "Stopped "); }
    private static int density(ServerCommandSource s, String n, float v) { return update(s,n,p -> p.withDensity(v), "Updated density for "); }
    private static int rate(ServerCommandSource s, String n, int v) { return update(s,n,p -> p.withRefreshTicks(v), "Updated rate for "); }
    private static int seed(ServerCommandSource s, String n, long v) { return update(s,n,p -> p.withSeed(v), "Updated seed for "); }
    private static int update(ServerCommandSource s, String n, java.util.function.UnaryOperator<GlitchPlane> op, String ok) { if (GlitchPlaneManager.update(world(s),n,op).isEmpty()) { s.sendError(Text.literal("Unknown glitch plane: " + n)); return 0; } GlitchPlaneManager.broadcast(world(s)); s.sendFeedback(() -> Text.literal(ok + n), true); return 1; }
    private static int remove(ServerCommandSource s, String n) { if (GlitchPlaneManager.remove(world(s),n).isEmpty()) { s.sendError(Text.literal("Unknown glitch plane: " + n)); return 0; } GlitchPlaneManager.broadcast(world(s)); s.sendFeedback(() -> Text.literal("Removed glitch plane " + n), true); return 1; }
    private static int list(ServerCommandSource s) { var planes = GlitchPlaneManager.all(world(s)); s.sendFeedback(() -> Text.literal(planes.isEmpty() ? "No glitch planes in this dimension." : "Glitch planes: " + String.join(", ", planes.stream().map(p -> p.name() + (p.enabled() ? " (on)" : " (off)")).toList())), false); return planes.size(); }

    private GlitchPlaneCommand() {}
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess access, CommandManager.RegistrationEnvironment environment) {
        var root = CommandManager.literal("monvhua")
                .requires(source -> source.hasPermissionLevel(2));
        var glitch = CommandManager.literal("glitch");

        glitch.then(CommandManager.literal("create")
                .then(CommandManager.argument("name", StringArgumentType.word())
                        .then(CommandManager.argument("from", BlockPosArgumentType.blockPos())
                                .then(CommandManager.argument("to", BlockPosArgumentType.blockPos())
                                        .executes(c -> create(
                                                c.getSource(),
                                                StringArgumentType.getString(c, "name"),
                                                BlockPosArgumentType.getLoadedBlockPos(c, "from"),
                                                BlockPosArgumentType.getLoadedBlockPos(c, "to")
                                        ))))));
        glitch.then(CommandManager.literal("start")
                .then(CommandManager.argument("name", StringArgumentType.word())
                        .executes(c -> setEnabled(c.getSource(), StringArgumentType.getString(c, "name"), true))));
        glitch.then(CommandManager.literal("stop")
                .then(CommandManager.argument("name", StringArgumentType.word())
                        .executes(c -> setEnabled(c.getSource(), StringArgumentType.getString(c, "name"), false))));
        glitch.then(CommandManager.literal("remove")
                .then(CommandManager.argument("name", StringArgumentType.word())
                        .executes(c -> remove(c.getSource(), StringArgumentType.getString(c, "name")))));
        glitch.then(CommandManager.literal("list").executes(c -> list(c.getSource())));

        var set = CommandManager.literal("set");
        set.then(CommandManager.argument("name", StringArgumentType.word())
                .then(CommandManager.literal("density")
                        .then(CommandManager.argument("value", FloatArgumentType.floatArg(0F, 1F))
                                .executes(c -> density(c.getSource(), StringArgumentType.getString(c, "name"),
                                        FloatArgumentType.getFloat(c, "value")))))
                .then(CommandManager.literal("rate")
                        .then(CommandManager.argument("ticks", IntegerArgumentType.integer(1, 20))
                                .executes(c -> rate(c.getSource(), StringArgumentType.getString(c, "name"),
                                        IntegerArgumentType.getInteger(c, "ticks")))))
                .then(CommandManager.literal("seed")
                        .then(CommandManager.argument("value", LongArgumentType.longArg())
                                .executes(c -> seed(c.getSource(), StringArgumentType.getString(c, "name"),
                                        LongArgumentType.getLong(c, "value"))))));
        glitch.then(set);
        root.then(glitch);
        dispatcher.register(root);
    }
 }
