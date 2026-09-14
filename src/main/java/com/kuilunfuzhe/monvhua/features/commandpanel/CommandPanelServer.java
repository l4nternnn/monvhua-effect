package com.kuilunfuzhe.monvhua.features.commandpanel;

import com.kuilunfuzhe.monvhua.network.commandpanel.CommandPanelPackets;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.text.Text;
import java.util.*;

public final class CommandPanelServer {
    private static final Map<UUID, List<UUID>> PENDING = new HashMap<>();
    private static final String EDIT_TAG = "ui_edit";
    private CommandPanelServer() {}
    private static boolean canEdit(ServerPlayerEntity player) {
        return player.isCreative() && player.getCommandTags().contains(EDIT_TAG);
    }
    public static void initialize() {
        CommandPanelPackets.SyncRequestS2C.register();
        CommandPanelPackets.SharedPanelS2C.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
            CommandManager.literal("commandpanel").then(CommandManager.literal("sync")
                .executes(ctx -> { ctx.getSource().sendError(Text.translatable("command.monvhua.commandpanel.sync.usage")); return 0; })
                .requires(source -> source.getEntity() instanceof ServerPlayerEntity p && canEdit(p))
                .then(CommandManager.argument("targets", EntityArgumentType.players()).executes(ctx -> {
                    ServerPlayerEntity sender = ctx.getSource().getPlayerOrThrow();
                    ServerWorld world = sender.getServer().getOverworld();
                    List<UUID> targets = EntityArgumentType.getPlayers(ctx, "targets").stream().map(ServerPlayerEntity::getUuid).toList();
                    PENDING.put(sender.getUuid(), targets);
                    ServerPlayNetworking.send(sender, new CommandPanelPackets.SyncRequestS2C(targets.stream().map(UUID::toString).collect(java.util.stream.Collectors.joining(","))));
                    int count = targets.size();
                    int synced = count;
                    ctx.getSource().sendFeedback(() -> Text.translatable("command.monvhua.commandpanel.sync.success", synced), false);
                    return count;
                })))));
        ServerPlayNetworking.registerGlobalReceiver(CommandPanelPackets.RequestC2S.ID, (packet, context) -> context.server().execute(() -> {
            ServerPlayerEntity player = context.player();
            ServerWorld world = player.getServer().getOverworld();
            ServerPlayNetworking.send(player, new CommandPanelPackets.PermissionS2C(canEdit(player)));

        }));
        ServerPlayNetworking.registerGlobalReceiver(CommandPanelPackets.SyncUploadC2S.ID, (packet, context) -> context.server().execute(() -> {
            ServerPlayerEntity sender = context.player();
            if (!canEdit(sender) || packet.json().length() > 32767) return;
            List<UUID> targets = PENDING.remove(sender.getUuid());
            if (targets == null) return;
            long revision = System.currentTimeMillis();
            for (UUID id : targets) { ServerPlayerEntity target = sender.getServer().getPlayerManager().getPlayer(id); if (target != null) ServerPlayNetworking.send(target, new CommandPanelPackets.SharedPanelS2C(sender.getName().getString(), revision, packet.json())); }
        }));
        ServerPlayNetworking.registerGlobalReceiver(CommandPanelPackets.SyncDecisionC2S.ID, (packet, context) -> context.server().execute(() -> { }));

        ServerPlayNetworking.registerGlobalReceiver(CommandPanelPackets.ExecuteC2S.ID, (packet, context) -> context.server().execute(() -> {
            ServerPlayerEntity player = context.player();
            String command = packet.command();
            if (command == null || command.length() > 32767 || command.indexOf('\n') >= 0) return;
            player.getServer().getCommandManager().executeWithPrefix(player.getCommandSource(), command.startsWith("/") ? command.substring(1) : command);
        }));
    }
}
