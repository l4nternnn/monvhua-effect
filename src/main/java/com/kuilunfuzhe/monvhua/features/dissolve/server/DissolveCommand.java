package com.kuilunfuzhe.monvhua.features.dissolve.server;

import com.kuilunfuzhe.monvhua.features.dissolve.DissolveFeature;
import com.kuilunfuzhe.monvhua.features.dissolve.DissolveProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class DissolveCommand {
    private DissolveCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess registryAccess,
                                CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal("monvhua-dissolve_消散")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.argument("target", EntityArgumentType.player())
                        .executes(context -> start(context.getSource(), EntityArgumentType.getPlayer(context, "target"),
                                DissolveProfile.DEFAULT.durationTicks()))
                        .then(CommandManager.argument("durationTicks", IntegerArgumentType.integer(1, 20 * 60))
                                .executes(context -> start(context.getSource(),
                                        EntityArgumentType.getPlayer(context, "target"),
                                        IntegerArgumentType.getInteger(context, "durationTicks")))))
                .then(CommandManager.literal("stop")
                        .then(CommandManager.argument("target", EntityArgumentType.player())
                                .executes(context -> stop(context.getSource(), EntityArgumentType.getPlayer(context, "target"))))));
    }

    private static int start(ServerCommandSource source, ServerPlayerEntity target, int durationTicks) {
        DissolveProfile profile = DissolveProfile.DEFAULT.withDurationTicks(durationTicks);
        if (!DissolveFeature.start(target, profile)) {
            source.sendError(Text.literal("无法开始消散效果: " + target.getName().getString()));
            return 0;
        }
        source.sendFeedback(() -> Text.literal("已开始消散: " + target.getName().getString()
                + " (" + durationTicks + " ticks)"), true);
        return 1;
    }

    private static int stop(ServerCommandSource source, ServerPlayerEntity target) {
        DissolveFeature.stop(target.getUuid(), DissolveFeature.StopReason.CANCELLED);
        source.sendFeedback(() -> Text.literal("已停止消散: " + target.getName().getString()), true);
        return 1;
    }
}
