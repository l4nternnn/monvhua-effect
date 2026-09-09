package com.kuilunfuzhe.monvhua.features.glitch.playerglitch;

import com.kuilunfuzhe.monvhua.network.playerglitch.PlayerGlitchPackets;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import java.util.*;

public final class PlayerGlitchServerController {
    private static final Map<UUID, State> STATES = new HashMap<>();
    private static int revision;
    private static int syncTick;
    private static boolean initialized;
    private PlayerGlitchServerController() {}
    public static void initialize() {
        if (initialized) return; initialized = true;
        CommandRegistrationCallback.EVENT.register(PlayerGlitchServerController::register);
        ServerPlayConnectionEvents.JOIN.register((h, s, server) -> server.execute(() -> syncWorld(h.getPlayer())));
        ServerPlayConnectionEvents.DISCONNECT.register((h, server) -> STATES.remove(h.getPlayer().getUuid()));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> syncWorld(player));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++syncTick < 20 || STATES.isEmpty()) return;
            syncTick = 0;
            for (State state : List.copyOf(STATES.values())) broadcast(server, state);
        });
    }
    private static void register(CommandDispatcher<ServerCommandSource> d, CommandRegistryAccess a, CommandManager.RegistrationEnvironment e) {
        var root = CommandManager.literal("monvhua").requires(s -> s.hasPermissionLevel(2));
        var command = CommandManager.literal("playerglitch");
        command.then(CommandManager.literal("start")
                .then(CommandManager.argument("target", EntityArgumentType.player())
                        .executes(c -> start(c.getSource(), EntityArgumentType.getPlayer(c, "target")))));
        command.then(CommandManager.literal("stop")
                .then(CommandManager.argument("target", EntityArgumentType.player())
                        .executes(c -> stop(c.getSource(), EntityArgumentType.getPlayer(c, "target")))));
        var limitCommand = CommandManager.literal("limit");
        var limitTarget = CommandManager.argument("target", EntityArgumentType.player());
        var limitFragments = CommandManager.argument("fragments", IntegerArgumentType.integer(1, 256));
        var limitOffset = CommandManager.argument("offset", FloatArgumentType.floatArg(0F, .12F));
        limitOffset.executes(c -> limit(
                c.getSource(),
                EntityArgumentType.getPlayer(c, "target"),
                IntegerArgumentType.getInteger(c, "fragments"),
                FloatArgumentType.getFloat(c, "offset")
        ));
        limitFragments.then(limitOffset);
        limitTarget.then(limitFragments);
        limitCommand.then(limitTarget);
        command.then(limitCommand);
        root.then(command);
        d.register(root);
    }
    private static int start(ServerCommandSource source, ServerPlayerEntity target) {
        State state = new State(target.getUuid(), true, new Random().nextLong(), 192, .06F, ++revision);
        STATES.put(target.getUuid(), state); broadcast(target.getServer(), state);
        source.sendFeedback(() -> Text.literal("Player glitch enabled for " + target.getName().getString()), true); return 1;
    }
    private static int stop(ServerCommandSource source, ServerPlayerEntity target) {
        STATES.remove(target.getUuid()); broadcast(target.getServer(), new State(target.getUuid(), false, 0L, 1, 0F, ++revision));
        source.sendFeedback(() -> Text.literal("Player glitch disabled for " + target.getName().getString()), true); return 1;
    }
    private static int limit(ServerCommandSource source, ServerPlayerEntity target, int fragments, float offset) {
        State old = STATES.get(target.getUuid());
        if (old == null) { source.sendError(Text.literal("Player glitch is not enabled")); return 0; }
        State state = new State(old.target(), true, old.seed(), fragments, offset, ++revision);
        STATES.put(target.getUuid(), state); broadcast(target.getServer(), state); return 1;
    }
    private static void broadcast(net.minecraft.server.MinecraftServer server, State state) {
        if (server == null) return;
        ServerPlayerEntity target = server.getPlayerManager().getPlayer(state.target());
        if (target == null) return;
        for (ServerPlayerEntity viewer : server.getPlayerManager().getPlayerList()) {
            if (viewer.getWorld() == target.getWorld() && viewer.squaredDistanceTo(target) <= 96.0 * 96.0)
                ServerPlayNetworking.send(viewer, new PlayerGlitchPackets.StateS2C(state.target(), state.enabled(), state.seed(), state.maxFragments(), state.maxOffset(), state.revision()));
        }
    }
    private static void syncWorld(ServerPlayerEntity viewer) {
        if (viewer == null) return;
        for (State state : STATES.values()) {
            ServerPlayerEntity target = viewer.getServer() == null ? null : viewer.getServer().getPlayerManager().getPlayer(state.target());
            if (target != null && target.getWorld() == viewer.getWorld() && viewer.squaredDistanceTo(target) <= 96.0 * 96.0)
                ServerPlayNetworking.send(viewer, new PlayerGlitchPackets.StateS2C(state.target(), state.enabled(), state.seed(), state.maxFragments(), state.maxOffset(), state.revision()));
        }
    }
    private record State(UUID target, boolean enabled, long seed, int maxFragments, float maxOffset, int revision) {}
}
