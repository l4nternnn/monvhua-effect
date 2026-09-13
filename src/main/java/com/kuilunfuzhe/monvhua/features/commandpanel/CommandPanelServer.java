package com.kuilunfuzhe.monvhua.features.commandpanel;

import com.kuilunfuzhe.monvhua.network.commandpanel.CommandPanelPackets;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.text.Text;

public final class CommandPanelServer {
    private static final String EDIT_TAG = "ui_edit";
    private CommandPanelServer() {}
    private static boolean canEdit(ServerPlayerEntity player) {
        return player.isCreative() && player.getCommandTags().contains(EDIT_TAG);
    }
    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
            CommandManager.literal("commandpanel").then(CommandManager.literal("sync")
                .requires(source -> source.getEntity() instanceof ServerPlayerEntity p && canEdit(p))
                .then(CommandManager.argument("targets", EntityArgumentType.players()).executes(ctx -> {
                    ServerPlayerEntity sender = ctx.getSource().getPlayerOrThrow();
                    ServerWorld world = sender.getServer().getOverworld();
                    CommandPanelStore store = CommandPanelStore.get(world);
                    String json = store.get(sender.getUuid());
                    int count = 0;
                    for (ServerPlayerEntity target : EntityArgumentType.getPlayers(ctx, "targets")) {
                        if (target.getUuid().equals(sender.getUuid())) continue;
                        store.put(target.getUuid(), json);
                        ServerPlayNetworking.send(target, new CommandPanelPackets.DataS2C(store.revision(target.getUuid()), json));
                        target.sendMessage(Text.literal(sender.getName().getString() + " synced a command panel to you"), false);
                        count++;
                    }
                    ctx.getSource().sendFeedback(() -> Text.literal("Command panel synchronized"), false);
                    return count;
                })))));
        ServerPlayNetworking.registerGlobalReceiver(CommandPanelPackets.RequestC2S.ID, (packet, context) -> context.server().execute(() -> {
            ServerPlayerEntity player = context.player();
            ServerWorld world = player.getServer().getOverworld();
            ServerPlayNetworking.send(player, new CommandPanelPackets.PermissionS2C(canEdit(player)));
            CommandPanelStore store = CommandPanelStore.get(world);
            ServerPlayNetworking.send(player, new CommandPanelPackets.DataS2C(store.revision(player.getUuid()), store.get(player.getUuid())));
        }));
        ServerPlayNetworking.registerGlobalReceiver(CommandPanelPackets.SaveC2S.ID, (packet, context) -> context.server().execute(() -> {
            ServerPlayerEntity player = context.player();
            if (packet.json().length() > 32767) return;
            ServerWorld world = player.getServer().getOverworld();
            {
                CommandPanelStore.get(world).put(player.getUuid(), packet.json());
            }
        }));
        ServerPlayNetworking.registerGlobalReceiver(CommandPanelPackets.ExecuteC2S.ID, (packet, context) -> context.server().execute(() -> {
            ServerPlayerEntity player = context.player();
            String command = packet.command();
            if (command == null || command.length() > 32767 || command.indexOf('\n') >= 0) return;
            player.getServer().getCommandManager().executeWithPrefix(player.getCommandSource(), command.startsWith("/") ? command.substring(1) : command);
        }));
    }
}
