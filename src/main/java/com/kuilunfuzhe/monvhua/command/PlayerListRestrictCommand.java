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

/**
 * 玩家列表限制命令（全局开关）。
 * 用法：/restrict playerlist true/false
 * 启用后，服务器所有生存/冒险模式的玩家将无法通过Tab键打开玩家列表。
 */
public final class PlayerListRestrictCommand {

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
     * 执行全局玩家列表限制开关，广播给所有在线玩家并持久化状态。
     */
    private static int execute(CommandContext<ServerCommandSource> context) {
        boolean value = BoolArgumentType.getBool(context, "value");

        // 更新配置并持久化到磁盘
        PlayerListRestrictConfig.getInstance().setEnabled(value);

        // 广播给所有在线玩家
        PlayerListRestrictS2CPacket packet = new PlayerListRestrictS2CPacket(value);
        for (ServerPlayerEntity player : context.getSource().getServer().getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, packet);
        }

        String status = value ? "§c已启用" : "§a已禁用";
        context.getSource().sendFeedback(
                () -> Text.literal(status + " 玩家列表限制（全局）"),
                true
        );
        return 1;
    }

    /**
     * 查询当前全局玩家列表限制是否启用。
     */
    public static boolean isRestricted() {
        return PlayerListRestrictConfig.getInstance().isEnabled();
    }
}
