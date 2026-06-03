package com.kuilunfuzhe.monvhua.item.secrecy;

import com.kuilunfuzhe.monvhua.config.GlobalConfigManager;
import com.kuilunfuzhe.monvhua.item.config.SecrecyConfig;
import com.kuilunfuzhe.monvhua.network.secrecy.SecrecyStateS2CPacket;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.consume.UseAction;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 隐秘物品，右键长按进入隐秘（隐身）状态。
 * 核心机制：使用后先进入准备阶段（施加失明/黑暗效果），
 * 延迟一定时间后获得隐身效果；松开右键或条件不满足时退出隐秘。
 * 特色：根据玩家阶段的速度修正、心跳音效、客户端状态同步。
 */
public class SecrecyItem extends Item {
    /** 隐秘物品的注册ID */
    public static final Identifier ID = Identifier.of("monvhua", "secrecy");
    /** 心跳音效的注册ID，隐秘状态下周期性播放，模拟紧张氛围 */
    public static final Identifier HEART_SOUND_ID = Identifier.of("monvhua", "heart");
    /** 隐秘速度修正器的ID，用于给隐秘状态的玩家施加移动速度倍率 */
    public static final Identifier SPEED_MODIFIER_ID = Identifier.of("monvhua", "secrecy_speed_multiplier");
    /** 隐秘物品单例，在initialize()中赋值 */
    public static SecrecyItem SECRECY_ITEM;
    /** 心跳音效事件，在initialize()中注册 */
    public static SoundEvent HEART_SOUND;

    /** -1表示无限时长药水效果 */
    private static final int INFINITE_DURATION = -1;
    /** 心跳音效播放间隔（tick），40 tick ≈ 2秒 */
    private static final int HEART_SOUND_INTERVAL_TICKS = 40;
    /** 心跳音效最大音量 */
    private static final float HEART_SOUND_MAX_VOLUME = 1.2F;
    /** 沉默标签，有此标签的玩家无法使用隐秘物品 */
    private static final String SILENCED_TAG = "Silenced";
    /** 等待消失的玩家映射：UUID → 世界时间（tick），到达时间后施加隐身效果 */
    private static final Map<UUID, Long> VANISH_PENDING_TICKS = new ConcurrentHashMap<>();
    /** 心跳音效倒计时：UUID → 剩余tick数，为0时播放音效并重置 */
    private static final Map<UUID, Integer> HEART_SOUND_TICKS = new ConcurrentHashMap<>();
    /** 当前处于隐秘状态的玩家集合 */
    private static final Set<UUID> ACTIVE_SECRECY = ConcurrentHashMap.newKeySet();
    /** 正在退出隐秘状态的玩家集合，用于tick循环中跳过处理 */
    private static final Set<UUID> EXITING_SECRECY = ConcurrentHashMap.newKeySet();
    /** 全局配置管理器，由外部在initialize()时注入 */
    private static GlobalConfigManager configManager;

    public SecrecyItem(Settings settings) {
        super(settings);
    }

    /**
     * 右键使用隐秘物品，进入隐秘准备状态。
     * 仅主手持有隐秘物品时生效；服务端检查Silenced标签和阶段配置后，
     * 若未处于隐秘状态则进入隐秘准备阶段（施加失明/黑暗，延迟后隐身）。
     */
    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        if (hand != Hand.MAIN_HAND || !isHoldingSecrecy(user.getStackInHand(hand))) {
            return ActionResult.PASS;
        }

        if (!world.isClient() && user instanceof ServerPlayerEntity player) {
            if (!canUseSecrecy(player, true)) {
                return ActionResult.FAIL;
            }
            user.setCurrentHand(hand);
            if (isSecrecyActive(player)) {
                enterSecrecy(player);
                player.sendMessage(Text.literal("§b正在集中精神..."), true);
            }
        } else {
            user.setCurrentHand(hand);
        }
        return ActionResult.SUCCESS;
    }

    /**
     * 使用过程中的每tick回调。
     * 服务端检查：若玩家尚未进入隐秘状态且满足条件，则自动进入隐秘。
     * 若玩家在使用过程中失去使用资格（如被添加Silenced标签），则强制停止使用。
     */
    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        if (!world.isClient() && user instanceof ServerPlayerEntity player && isSecrecyActive(player)) {
            if (!canUseSecrecy(player, true)) {
                player.stopUsingItem();
                return;
            }
            enterSecrecy(player);
            player.sendMessage(Text.literal("§b正在集中精神..."), true);
        }
    }

    /**
     * 玩家停止使用物品时的回调（松开右键）。
     * 服务端调用exitSecrecy退出隐秘状态。
     */
    @Override
    public boolean onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (!world.isClient() && user instanceof ServerPlayerEntity player) {
            exitSecrecy(player);
        }
        return true;
    }

    /** 物品最大使用时间（tick），72000 tick ≈ 60分钟，即几乎等同于无限使用 */
    @Override
    public int getMaxUseTime(ItemStack stack, LivingEntity user) {
        return 72000;
    }

    /** 使用动作表现为拉弓姿势，与右键长按体感一致 */
    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.BOW;
    }

    /**
     * 静态初始化：注册心跳音效、创建隐秘物品实例并注册到物品注册表，
     * 同时注册服务端tick事件处理隐秘状态的持续逻辑和消失延迟倒计时。
     * @param manager 全局配置管理器，用于读取各阶段的隐秘参数
     */
    public static void initialize(GlobalConfigManager manager) {
        configManager = manager;
        HEART_SOUND = Registry.register(Registries.SOUND_EVENT, HEART_SOUND_ID, SoundEvent.of(HEART_SOUND_ID));
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, ID);
        SECRECY_ITEM = new SecrecyItem(new Item.Settings().registryKey(key).maxCount(1));
        Registry.register(Registries.ITEM, ID, SECRECY_ITEM);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(SECRECY_ITEM));

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long now = server.getOverworld().getTime();
            Set<UUID> activePlayers = new HashSet<>();
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (EXITING_SECRECY.remove(player.getUuid())) {
                    continue;
                }
                if (isSecrecyActive(player)) continue;
                if (!shouldContinueSecrecy(player)) {
                    exitSecrecy(player);
                    continue;
                }
                activePlayers.add(player.getUuid());
                playHeartSound(player);
            }
            HEART_SOUND_TICKS.keySet().removeIf(uuid -> !activePlayers.contains(uuid));

            Iterator<Map.Entry<UUID, Long>> iterator = VANISH_PENDING_TICKS.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, Long> entry = iterator.next();
                if (entry.getValue() > now) continue;
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
                if (player != null && shouldContinueSecrecy(player)) {
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, INFINITE_DURATION, 0, false, false, true));
                    syncSecrecyState(player, true);
                    player.sendMessage(Text.literal("§b精神集中..."), true);
                }
                iterator.remove();
            }
        });
    }

    /** 检查物品堆是否就是隐秘物品本身 */
    public static boolean isHoldingSecrecy(ItemStack stack) {
        return SECRECY_ITEM != null && stack.getItem() == SECRECY_ITEM;
    }

    /**
     * 获取玩家的天眼阶段值（1-8），用于查询对应阶段的隐秘配置参数。
     * @return 阶段值，若配置管理器未初始化则默认返回1
     */
    public static int getPlayerStage(ServerPlayerEntity player) {
        if (configManager == null) return 1;
        return com.kuilunfuzhe.monvhua.features.evil_eyes.Evil_Eyes.getPlayerStage(player, configManager);
    }

    /**
     * 强制退出隐秘状态。
     * 清除玩家的消失等待、心跳计时、速度修正、隐身/失明/黑暗效果，
     * 并将玩家加入EXITING_SECRECY集合以在下一tick跳过处理。
     */
    public static void exitSecrecy(ServerPlayerEntity player) {
        if (isSecrecyActive(player)) {
            return;
        }
        VANISH_PENDING_TICKS.remove(player.getUuid());
        HEART_SOUND_TICKS.remove(player.getUuid());
        ACTIVE_SECRECY.remove(player.getUuid());
        EXITING_SECRECY.add(player.getUuid());
        removeSpeedModifier(player);
        player.removeStatusEffect(StatusEffects.INVISIBILITY);
        //player.removeStatusEffect(StatusEffects.BLINDNESS);
        //player.removeStatusEffect(StatusEffects.DARKNESS);
        syncSecrecyState(player, false);
    }

    private static void enterSecrecy(ServerPlayerEntity player) {
        int stage = getPlayerStage(player);
        SecrecyConfig config = SecrecyConfig.getInstance();
        double speedMultiplier = config.getSpeedMultiplier(stage);
        int delaySeconds = config.getVanishDelaySeconds(stage);
        int delayTicks = delaySeconds * 20;

        ACTIVE_SECRECY.add(player.getUuid());
        applySpeedMultiplier(player, speedMultiplier);
        playHeartSound(player);
        EXITING_SECRECY.remove(player.getUuid());
        //player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, INFINITE_DURATION, 0, false, false, false));
        //player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, INFINITE_DURATION, 0, false, false, false));
        //player.removeStatusEffect(StatusEffects.INVISIBILITY);
        syncSecrecyState(player, false);
        VANISH_PENDING_TICKS.put(player.getUuid(), player.getWorld().getTime() + delayTicks);
    }

    private static boolean isSecrecyActive(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        return !ACTIVE_SECRECY.contains(uuid)
                && !VANISH_PENDING_TICKS.containsKey(uuid)
                && !hasSpeedModifier(player);
    }

    private static boolean hasPreparationEffects(ServerPlayerEntity player) {
        return player.hasStatusEffect(StatusEffects.BLINDNESS)
                && player.hasStatusEffect(StatusEffects.DARKNESS);
    }

    private static boolean shouldContinueSecrecy(ServerPlayerEntity player) {
        return player.isUsingItem()
                && player.getActiveHand() == Hand.MAIN_HAND
                && isHoldingSecrecy(player.getMainHandStack())
                && canUseSecrecy(player, false);
    }

    private static boolean canUseSecrecy(ServerPlayerEntity player, boolean notify) {
        if (player.getCommandTags().contains(SILENCED_TAG)) {
            if (notify) {
                player.sendMessage(Text.literal("§c你难以集中精神"), true);
            }
            return false;
        }

        int stage = getPlayerStage(player);
        if (SecrecyConfig.getInstance().getVanishDelaySeconds(stage) < 0) {
            if (notify) {
                player.sendMessage(Text.literal("§c你难以集中精神"), true);
            }
            return false;
        }
        return true;
    }

    private static void applySpeedMultiplier(ServerPlayerEntity player, double multiplier) {
        EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
        if (speed == null) return;
        speed.removeModifier(SPEED_MODIFIER_ID);
        speed.addTemporaryModifier(new EntityAttributeModifier(
                SPEED_MODIFIER_ID,
                Math.max(0.0D, multiplier) - 1.0D,
                EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        ));
    }

    private static void removeSpeedModifier(ServerPlayerEntity player) {
        EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SPEED_MODIFIER_ID);
        }
    }

    private static boolean hasSpeedModifier(ServerPlayerEntity player) {
        EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
        return speed != null && speed.hasModifier(SPEED_MODIFIER_ID);
    }

    private static void playHeartSound(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        int ticks = HEART_SOUND_TICKS.getOrDefault(uuid, 0);
        if (ticks > 0) {
            HEART_SOUND_TICKS.put(uuid, ticks - 1);
            return;
        }
        player.playSoundToPlayer(HEART_SOUND, SoundCategory.PLAYERS, HEART_SOUND_MAX_VOLUME, 1.0F);
        HEART_SOUND_TICKS.put(uuid, HEART_SOUND_INTERVAL_TICKS);
    }

    private static void syncSecrecyState(ServerPlayerEntity player, boolean invisible) {
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new SecrecyStateS2CPacket(invisible, 0));
    }
}
