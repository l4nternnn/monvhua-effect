package com.kuilunfuzhe.monvhua.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.kuilunfuzhe.monvhua.entity.ModEntities;
import com.kuilunfuzhe.monvhua.entity.TestMannequinEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.literal;

/**
 * 模组命令注册入口，负责将所有命令注册到 Brigadier 命令分发器。
 * 当前仅包含测试用的 placemannequin_放置测试模型 命令。
 */
public class ModCommands {
    /**
     * 向命令分发器注册所有模组命令。
     * @param dispatcher Minecraft 服务端命令分发器
     */
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("placemannequin_放置测试模型")
                .executes(ModCommands::placeMannequin));
    }

    /**
     * 在玩家当前位置生成一个测试模型实体，用于验证模型渲染效果。
     * @param ctx 命令上下文
     * @return 1 表示成功，0 表示玩家不存在
     */
    private static int placeMannequin(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        TestMannequinEntity mannequin = new TestMannequinEntity(ModEntities.TEST_MANNEQUIN, player.getWorld());
        mannequin.setPosition(player.getX(), player.getY(), player.getZ());
        player.getWorld().spawnEntity(mannequin);
        player.sendMessage(Text.literal("§a已放置测试模型实体"), false);
        return 1;
    }
}
