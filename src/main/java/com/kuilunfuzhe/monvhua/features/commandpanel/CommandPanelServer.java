package com.kuilunfuzhe.monvhua.features.commandpanel;

import com.kuilunfuzhe.monvhua.network.commandpanel.CommandPanelPackets;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

public final class CommandPanelServer {
    private static final String EDIT_TAG = "ui_edit";
    private CommandPanelServer() {}
    private static boolean canEdit(ServerPlayerEntity player) {
        return player.isCreative() && player.getCommandTags().contains(EDIT_TAG);
    }
    public static void initialize() {
        ServerPlayNetworking.registerGlobalReceiver(CommandPanelPackets.RequestC2S.ID, (packet, context) -> context.server().execute(() -> {
            ServerPlayerEntity player = context.player();
            if (!(player.getWorld() instanceof ServerWorld world)) return;
            ServerPlayNetworking.send(player, new CommandPanelPackets.PermissionS2C(canEdit(player)));
            ServerPlayNetworking.send(player, new CommandPanelPackets.DataS2C(CommandPanelStore.get(world).get(player.getUuid())));
        }));
        ServerPlayNetworking.registerGlobalReceiver(CommandPanelPackets.SaveC2S.ID, (packet, context) -> context.server().execute(() -> {
            ServerPlayerEntity player = context.player();
            if (!canEdit(player) || packet.json().length() > 32767) return;
            if (player.getWorld() instanceof ServerWorld world) {
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
