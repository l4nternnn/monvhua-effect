package com.kuilunfuzhe.monvhua.command;

import com.kuilunfuzhe.monvhua.network.playerlist.PlayerListRestrictS2CPacket;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 玩家列表限制命令。
 * 用法：/restrict playerlist true/false
 * 启用后，生存/冒险模式的玩家将无法通过Tab键打开玩家列表。
 */
public final class PlayerListRestrictCommand {

    /** 当前被限制玩家列表的玩家 UUID 集合 */
    private static final Set<UUID> RESTRICTED_PLAYERS = new HashSet<>();

    private PlayerListRestrictCommand() {
    }

    /**
     * 注册命令到 Brigadier 分发器。
     */
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                net.minecraft.registry.RegistryWrapper.WrapperLookup registryAccess,
                                CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(restrictRoot("restrict_限制"));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> restrictRoot(String name) {
        return CommandManager.literal(name)
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("playerlist_玩家列表")
                        .then(CommandManager.argument("value", BoolArgumentType.bool())
                                .executes(PlayerListRestrictCommand::execute)));
    }

    /**
     * 执行限制命令，切换执行者自身的玩家列表限制状态。
     */
    private static int execute(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendError(Text.literal("此命令只能由玩家执行"));
            return 0;
        }

        boolean value = BoolArgumentType.getBool(context, "value");
        UUID uuid = player.getUuid();

        if (value) {
            RESTRICTED_PLAYERS.add(uuid);
        } else {
            RESTRICTED_PLAYERS.remove(uuid);
        }

        ServerPlayNetworking.send(player, new PlayerListRestrictS2CPacket(value));

        String status = value ? "§c已启用" : "§a已禁用";
        context.getSource().sendFeedback(
                () -> Text.literal(status + " 玩家列表限制"),
                false
        );
        return 1;
    }

    /**
     * 查询指定玩家是否受玩家列表限制。
     */
    public static boolean isRestricted(ServerPlayerEntity player) {
        return RESTRICTED_PLAYERS.contains(player.getUuid());
    }

    /**
     * 玩家断线时清理限制状态。
     */
    public static void onPlayerDisconnect(ServerPlayerEntity player) {
        RESTRICTED_PLAYERS.remove(player.getUuid());
    }
}
