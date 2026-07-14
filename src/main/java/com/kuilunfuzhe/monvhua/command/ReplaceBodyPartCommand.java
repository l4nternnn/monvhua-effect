package com.kuilunfuzhe.monvhua.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.kuilunfuzhe.monvhua.features.block.body.BodyPartManager;
import com.kuilunfuzhe.monvhua.item.modblock.moditems.Assembly_ModItems;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.CommandSource;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;

import java.util.List;
import java.util.Set;

/**
 * /clairvoyance-肢体替换 命令，将玩家附近 3 格范围内的肢体展示实体的皮肤替换为内置皮肤或指定玩家的皮肤。
 * 支持 slim 手臂模型选项和分离整体肢体功能。
 */
public class ReplaceBodyPartCommand {
    /** 所有肢体物品（头、躯干、左/右臂、左/右腿），用于筛选附近的 ItemDisplayEntity */
    private static final Set<Item> BODY_PART_ITEMS = Set.of(
            Assembly_ModItems.HEAD_ITEM, Assembly_ModItems.TORSO_ITEM,
            Assembly_ModItems.LEFT_ARM_ITEM, Assembly_ModItems.RIGHT_ARM_ITEM,
            Assembly_ModItems.LEFT_LEG_ITEM, Assembly_ModItems.RIGHT_LEG_ITEM
    );

    /**
     * 注册 /clairvoyance-肢体替换 命令及其子命令（localskin / player / split）。
     */
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess registryAccess,
                                CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal("clairvoyance-body-replace_肢体替换")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("localskin_内置")
                        .then(CommandManager.argument("skinName", StringArgumentType.string())
                                .suggests((context, builder) -> CommandSource.suggestMatching(GiveBodyPartCommand.LOCAL_SKINS, builder))
                                .executes(ctx -> replaceWithLocalSkin(ctx, false))
                                .then(CommandManager.literal("slim_纤细")
                                        .executes(ctx -> replaceWithLocalSkin(ctx, true))
                                )
                        )
                )
                .then(CommandManager.literal("player_玩家")
                        .then(CommandManager.argument("playerName", StringArgumentType.string())
                                .executes(ctx -> replaceWithPlayer(ctx, false))
                                .then(CommandManager.literal("slim_纤细")
                                        .executes(ctx -> replaceWithPlayer(ctx, true))
                                )
                        )
                )
                .then(CommandManager.literal("split_分离")
                        .executes(ReplaceBodyPartCommand::splitCombined)
                )
        );
    }

    /**
     * 将附近肢体展示实体替换为指定内置皮肤。
     * @param slim 是否使用 slim 手臂模型
     */
    private static int replaceWithLocalSkin(CommandContext<ServerCommandSource> ctx, boolean slim) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        String skinName = StringArgumentType.getString(ctx, "skinName");
        int count = replaceItemDisplays(player, skinName, slim);
        ctx.getSource().sendFeedback(() -> Text.literal("已将 " + count + " 个肢体展示实体替换为内置皮肤 " + skinName), true);
        return count;
    }

    /**
     * 将附近肢体展示实体替换为指定在线玩家的皮肤。
     * @param slim 是否使用 slim 手臂模型
     */
    private static int replaceWithPlayer(CommandContext<ServerCommandSource> ctx, boolean slim) throws CommandSyntaxException {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        String targetName = StringArgumentType.getString(ctx, "playerName");
        ServerPlayerEntity target = source.getServer().getPlayerManager().getPlayer(targetName);
        if (target == null) {
            source.sendError(Text.literal("玩家 " + targetName + " 不在线"));
            return 0;
        }
        int count = replaceItemDisplays(player, target, slim);
        source.sendFeedback(() -> Text.literal("已将 " + count + " 个肢体展示实体替换为玩家 " + targetName), true);
        return count;
    }

    /**
     * 替换玩家周围 3 格范围内的肢体 ItemDisplayEntity 为内置皮肤。
     * @param localSkin 内置皮肤名称
     * @param slim 是否使用 slim 模型
     * @return 替换的实体数量
     */
    private static int replaceItemDisplays(ServerPlayerEntity player, String localSkin, boolean slim) {
        ServerWorld world = (ServerWorld) player.getWorld();
        Box box = new Box(player.getBlockPos()).expand(3);
        List<ItemDisplayEntity> entities = world.getEntitiesByClass(ItemDisplayEntity.class, box,
                e -> BODY_PART_ITEMS.contains(e.getItemStack().getItem()));

        for (ItemDisplayEntity display : entities) {
            Item item = display.getItemStack().getItem();
            ItemStack newStack = createStackWithLocalSkin(item, localSkin, slim);
            display.setItemStack(newStack);
        }
        return entities.size();
    }

    /**
     * 替换玩家周围 3 格范围内的肢体 ItemDisplayEntity 为目标玩家的皮肤（使用 PROFILE 组件）。
     * @param target 皮肤来源玩家
     * @param slim 是否使用 slim 模型
     * @return 替换的实体数量
     */
    private static int replaceItemDisplays(ServerPlayerEntity player, ServerPlayerEntity target, boolean slim) {
        ServerWorld world = (ServerWorld) player.getWorld();
        Box box = new Box(player.getBlockPos()).expand(3);
        List<ItemDisplayEntity> entities = world.getEntitiesByClass(ItemDisplayEntity.class, box,
                e -> BODY_PART_ITEMS.contains(e.getItemStack().getItem()));

        ProfileComponent profile = new ProfileComponent(target.getGameProfile());
        for (ItemDisplayEntity display : entities) {
            Item item = display.getItemStack().getItem();
            ItemStack newStack = createStackWithProfile(item, profile, slim);
            display.setItemStack(newStack);
        }
        return entities.size();
    }

    /**
     * 创建带有内置皮肤 NBT 数据的 ItemStack，通过 CUSTOM_DATA 组件存储 local_skin 和 arm_model。
     */
    private static ItemStack createStackWithLocalSkin(Item item, String skinName, boolean slim) {
        ItemStack stack = new ItemStack(item);
        NbtCompound nbt = new NbtCompound();
        nbt.putString("local_skin", skinName);
        if (slim) {
            nbt.putString("arm_model", "slim");
        }
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§6§k13§4" + skinName + "§r§6§k13§r的" + chineseName(item)));
        return stack;
    }

    /**
     * 创建带有 PROFILE 组件的 ItemStack，使其显示目标玩家的皮肤。
     */
    private static ItemStack createStackWithProfile(Item item, ProfileComponent profile, boolean slim) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponentTypes.PROFILE, profile);
        if (slim) {
            NbtCompound nbt = new NbtCompound();
            nbt.putString("arm_model", "slim");
            stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        }
        String name = profile.gameProfile().getName();
        if (name != null) {
            stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§6§k13§4" + name + "§r§6§k13§r的" + chineseName(item)));
        }
        return stack;
    }

    /**
     * 根据肢体物品返回其中文名称。
     * @return 中文名称，非肢体物品返回空字符串
     */
    private static String chineseName(Item item) {
        if (item == Assembly_ModItems.HEAD_ITEM) return "头";
        if (item == Assembly_ModItems.TORSO_ITEM) return "躯干";
        if (item == Assembly_ModItems.LEFT_ARM_ITEM) return "左臂";
        if (item == Assembly_ModItems.RIGHT_ARM_ITEM) return "右臂";
        if (item == Assembly_ModItems.LEFT_LEG_ITEM) return "左腿";
        if (item == Assembly_ModItems.RIGHT_LEG_ITEM) return "右腿";
        return "";
    }

    /**
     * 将玩家附近的整体肢体展示实体分离为单独的头/躯干/四肢展示实体。
     */
    private static int splitCombined(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        int count = BodyPartManager.splitCombinedBody(player);
        ctx.getSource().sendFeedback(() -> Text.literal("已将 " + count + " 个整体肢体分离为单独肢体"), true);
        return count;
    }
}
