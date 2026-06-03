package com.kuilunfuzhe.monvhua.command;

import com.kuilunfuzhe.monvhua.features.block.body_skeletal.SkeletalBodyPartBlockEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public class SkeletalBodyPartCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess registryAccess,
                                CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal("monvhua-skeletal-pose")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                        .then(CommandManager.argument("pitch", FloatArgumentType.floatArg(-180.0F, 180.0F))
                                .then(CommandManager.argument("yaw", FloatArgumentType.floatArg(-180.0F, 180.0F))
                                        .then(CommandManager.argument("roll", FloatArgumentType.floatArg(-180.0F, 180.0F))
                                                .executes(ctx -> {
                                                    BlockPos pos = BlockPosArgumentType.getLoadedBlockPos(ctx, "pos");
                                                    float pitch = FloatArgumentType.getFloat(ctx, "pitch");
                                                    float yaw = FloatArgumentType.getFloat(ctx, "yaw");
                                                    float roll = FloatArgumentType.getFloat(ctx, "roll");
                                                    return setPose(ctx.getSource(), pos, pitch, yaw, roll);
                                                }))))));
    }

    private static int setPose(ServerCommandSource source, BlockPos pos, float pitch, float yaw, float roll) {
        BlockEntity blockEntity = source.getWorld().getBlockEntity(pos);
        if (!(blockEntity instanceof SkeletalBodyPartBlockEntity skeletal)) {
            source.sendError(Text.literal("Target block is not a skeletal body part"));
            return 0;
        }

        skeletal.setJointPose(pitch, yaw, roll);
        source.sendFeedback(() -> Text.literal("Set " + skeletal.getBoneId() + " pose to pitch="
                + pitch + ", yaw=" + yaw + ", roll=" + roll), true);
        return 1;
    }
}
