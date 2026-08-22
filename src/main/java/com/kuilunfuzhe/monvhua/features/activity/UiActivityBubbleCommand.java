package com.kuilunfuzhe.monvhua.features.activity;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import java.util.Locale;

public final class UiActivityBubbleCommand {
    private static final String COMMAND = "monvhua-bubble-size";
    private static final String STYLE_COMMAND = "monvhua-bubble-style";

    private UiActivityBubbleCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal(COMMAND)
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.argument(
                                "multiplier",
                                FloatArgumentType.floatArg(
                                        UiActivityBubbleSize.MIN_MULTIPLIER,
                                        UiActivityBubbleSize.MAX_MULTIPLIER
                                )
                        )
                        .executes(context -> setMultiplier(
                                context.getSource(),
                                FloatArgumentType.getFloat(context, "multiplier")
                        )))
                .then(CommandManager.literal("reset")
                        .executes(context -> setMultiplier(
                                context.getSource(),
                                UiActivityBubbleSize.DEFAULT_MULTIPLIER
                        ))));
        dispatcher.register(CommandManager.literal(STYLE_COMMAND)
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("pixel")
                        .executes(context -> setStyle(context.getSource(), UiActivityBubbleStyle.PIXEL)))
                .then(CommandManager.literal("default")
                        .executes(context -> setStyle(context.getSource(), UiActivityBubbleStyle.DEFAULT)))
                .then(CommandManager.literal("reset")
                        .executes(context -> setStyle(context.getSource(), UiActivityBubbleStyle.DEFAULT))));
    }

    private static int setMultiplier(ServerCommandSource source, float multiplier) {
        float sanitized = UiActivityBubbleSize.sanitize(multiplier);
        UiActivityBubbleSizeStore.get(source.getServer()).setMultiplier(sanitized);
        UiActivityServer.broadcastBubbleSize(source.getServer(), sanitized);
        source.sendFeedback(
                () -> Text.literal(String.format(Locale.ROOT, "气泡尺寸倍率已设置为 %.2f", sanitized)),
                true
        );
        return 1;
    }

    private static int setStyle(ServerCommandSource source, UiActivityBubbleStyle style) {
        UiActivityBubbleStyleStore.get(source.getServer()).setStyle(style);
        UiActivityServer.broadcastBubbleStyle(source.getServer(), style);
        source.sendFeedback(() -> Text.literal("Activity bubble style set to "
                + style.name().toLowerCase(Locale.ROOT)), true);
        return 1;
    }
}
