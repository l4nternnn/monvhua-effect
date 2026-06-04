package com.kuilunfuzhe.monvhua.command;

import com.kuilunfuzhe.monvhua.features.cosmic_box.CosmicBoxBeamStyle;
import com.kuilunfuzhe.monvhua.features.cosmic_box.CosmicBoxBlockEntity;
import com.kuilunfuzhe.monvhua.features.cosmic_box.CosmicBoxNetworking;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.Arrays;

public final class CosmicBoxCommand {
    private static final double LOOK_DISTANCE = 64.0D;

    private CosmicBoxCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess registryAccess,
                                CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal("cosmicbox")
                .then(CommandManager.literal("beamstyle")
                        .then(CommandManager.argument("style", StringArgumentType.word())
                                .suggests((context, builder) -> CommandSource.suggestMatching(styleIds(), builder))
                                .executes(CosmicBoxCommand::setLookingStyle))
                        .then(CommandManager.literal("at")
                                .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                                        .then(CommandManager.argument("style", StringArgumentType.word())
                                                .suggests((context, builder) -> CommandSource.suggestMatching(styleIds(), builder))
                                                .executes(CosmicBoxCommand::setPosStyle))))));
    }

    private static int setLookingStyle(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendError(Text.literal("该用法只能由玩家执行"));
            return 0;
        }
        if (!canUse(context.getSource(), player)) {
            context.getSource().sendError(Text.literal("只有 OP、创造模式或拥有 zhuizong 标签的玩家可以切换宇宙盒光束样式"));
            return 0;
        }

        BlockPos pos = raycastBlock(player);
        if (pos == null) {
            context.getSource().sendError(Text.literal("准星没有指向方块"));
            return 0;
        }
        return setStyle(context.getSource(), pos, StringArgumentType.getString(context, "style"));
    }

    private static int setPosStyle(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (!canUse(context.getSource(), player)) {
            context.getSource().sendError(Text.literal("只有 OP、创造模式或拥有 zhuizong 标签的玩家可以切换宇宙盒光束样式"));
            return 0;
        }

        BlockPos pos = BlockPosArgumentType.getLoadedBlockPos(context, "pos");
        return setStyle(context.getSource(), pos, StringArgumentType.getString(context, "style"));
    }

    private static int setStyle(ServerCommandSource source, BlockPos pos, String styleId) {
        CosmicBoxBeamStyle style = CosmicBoxBeamStyle.byId(styleId);
        if (!style.id().equals(styleId.toLowerCase(java.util.Locale.ROOT))) {
            source.sendError(Text.literal("未知光束样式: " + styleId + "，可用: " + String.join(", ", styleIds())));
            return 0;
        }
        if (!(source.getWorld().getBlockEntity(pos) instanceof CosmicBoxBlockEntity cosmicBox)) {
            source.sendError(Text.literal("目标位置不是宇宙盒"));
            return 0;
        }

        cosmicBox.setBeamStyle(style);
        source.sendFeedback(() -> Text.literal("宇宙盒光束样式已切换为 " + style.id()), true);
        return 1;
    }

    private static boolean canUse(ServerCommandSource source, ServerPlayerEntity player) {
        return source.hasPermissionLevel(2) || player != null && CosmicBoxNetworking.canUseCosmicBox(player);
    }

    private static BlockPos raycastBlock(ServerPlayerEntity player) {
        Vec3d start = player.getEyePos();
        Vec3d end = start.add(player.getRotationVec(1.0F).multiply(LOOK_DISTANCE));
        BlockHitResult hit = player.getWorld().raycast(new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                player
        ));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : null;
    }

    private static Iterable<String> styleIds() {
        return Arrays.stream(CosmicBoxBeamStyle.values()).map(CosmicBoxBeamStyle::id)::iterator;
    }
}
