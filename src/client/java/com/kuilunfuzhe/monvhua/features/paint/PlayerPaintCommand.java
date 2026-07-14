package com.kuilunfuzhe.monvhua.features.paint;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.kuilunfuzhe.monvhua.network.SafeClientNetworking;
import com.kuilunfuzhe.monvhua.network.paint.PaintOverlayPackets;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class PlayerPaintCommand {
    private static final String COMMAND = "clearentitypaint_清除实体画迹";
    private static final String ALIAS = "clearplayerpaint_清除玩家画迹";

    private PlayerPaintCommand() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register(PlayerPaintCommand::registerCommands);
    }

    private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher, Object registryAccess) {
        dispatcher.register(buildCommand(COMMAND));
        dispatcher.register(buildCommand(ALIAS));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource> buildCommand(String name) {
        return ClientCommandManager.literal(name)
                .executes(context -> clearPaint(context.getSource(), "@s"))
                .then(ClientCommandManager.argument("targets", StringArgumentType.greedyString())
                        .suggests(PlayerPaintCommand::suggestTargets)
                        .executes(PlayerPaintCommand::clearPaint));
    }

    private static int clearPaint(CommandContext<FabricClientCommandSource> context) {
        return clearPaint(context.getSource(), StringArgumentType.getString(context, "targets"));
    }

    private static int clearPaint(FabricClientCommandSource source, String targetText) {
        List<PlayerEntity> targets = resolveTargets(source, targetText);
        if (targets.isEmpty()) {
            source.sendError(Text.literal("No matching painted players"));
            return 0;
        }

        int cleared = 0;
        for (PlayerEntity target : targets) {
            if (!PlayerSkinPaintManager.isPlayerPainted(target.getUuid())) {
                continue;
            }
            PlayerSkinPaintManager.resetTexture(target);
            SafeClientNetworking.send(new PaintOverlayPackets.ClearPlayerPaintC2S(target.getId()));
            cleared++;
        }

        source.sendFeedback(Text.literal("Cleared player paint: " + cleared + "/" + targets.size()));
        return cleared;
    }

    private static CompletableFuture<Suggestions> suggestTargets(CommandContext<FabricClientCommandSource> context,
                                                                 SuggestionsBuilder builder) {
        List<String> suggestions = new ArrayList<>();
        suggestions.add("@s");
        suggestions.add("@p");
        suggestions.add("@a");
        suggestions.add("@r");
        suggestions.add("@e[type=player]");
        if (context.getSource().getWorld() != null) {
            for (AbstractClientPlayerEntity player : context.getSource().getWorld().getPlayers()) {
                suggestions.add(player.getName().getString());
            }
        }
        return CommandSource.suggestMatching(suggestions, builder);
    }

    private static List<PlayerEntity> resolveTargets(FabricClientCommandSource source, String targetText) {
        if (source.getWorld() == null || source.getPlayer() == null) {
            return List.of();
        }

        TargetSelector selector = TargetSelector.parse(targetText);
        List<PlayerEntity> candidates = new ArrayList<>();
        if (selector.isName()) {
            for (AbstractClientPlayerEntity player : source.getWorld().getPlayers()) {
                if (player.getName().getString().equals(selector.name())) {
                    candidates.add(player);
                    break;
                }
            }
            return candidates;
        }

        switch (selector.base()) {
            case "@s" -> candidates.add(source.getPlayer());
            case "@p", "@r" -> candidates.addAll(source.getWorld().getPlayers());
            case "@a", "@e" -> candidates.addAll(source.getWorld().getPlayers());
            default -> {
                return List.of();
            }
        }

        candidates.removeIf(player -> !selector.matches(player));
        if (selector.base().equals("@p")) {
            candidates.sort(Comparator.comparingDouble(player -> player.squaredDistanceTo(source.getPosition())));
        } else if (selector.base().equals("@r")) {
            java.util.Collections.shuffle(candidates);
        }
        if (selector.limit() > 0 && candidates.size() > selector.limit()) {
            return new ArrayList<>(candidates.subList(0, selector.limit()));
        }
        return candidates;
    }

    private record TargetSelector(String base, String name, String nameFilter, boolean invertedName, int limit) {
        static TargetSelector parse(String raw) {
            String text = raw == null || raw.isBlank() ? "@s" : raw.trim();
            if (!text.startsWith("@")) {
                return new TargetSelector("", text, "", false, 0);
            }

            int bracket = text.indexOf('[');
            String base = bracket >= 0 ? text.substring(0, bracket) : text;
            String args = bracket >= 0 && text.endsWith("]") ? text.substring(bracket + 1, text.length() - 1) : "";
            String nameFilter = "";
            boolean invertedName = false;
            int limit = 0;
            boolean typeMatchesPlayer = true;

            if (!args.isBlank()) {
                for (String entry : args.split(",")) {
                    int eq = entry.indexOf('=');
                    if (eq <= 0) {
                        continue;
                    }
                    String key = entry.substring(0, eq).trim().toLowerCase(Locale.ROOT);
                    String value = entry.substring(eq + 1).trim();
                    if (key.equals("name")) {
                        invertedName = value.startsWith("!");
                        nameFilter = invertedName ? value.substring(1) : value;
                    } else if (key.equals("limit")) {
                        try {
                            limit = Math.max(0, Integer.parseInt(value));
                        } catch (NumberFormatException ignored) {
                        }
                    } else if (key.equals("type")) {
                        boolean inverted = value.startsWith("!");
                        String type = inverted ? value.substring(1) : value;
                        boolean playerType = type.equals("player") || type.equals("minecraft:player");
                        typeMatchesPlayer = inverted != playerType;
                    }
                }
            }

            if (!typeMatchesPlayer) {
                return new TargetSelector("", "", "", false, 0);
            }
            return new TargetSelector(base, "", nameFilter, invertedName, limit);
        }

        boolean isName() {
            return !name.isEmpty();
        }

        boolean matches(PlayerEntity player) {
            if (nameFilter.isEmpty()) {
                return true;
            }
            boolean matches = player.getName().getString().equals(nameFilter);
            return invertedName != matches;
        }
    }
}
