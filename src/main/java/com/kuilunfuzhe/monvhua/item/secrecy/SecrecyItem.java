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

public class SecrecyItem extends Item {
    public static final Identifier ID = Identifier.of("monvhua", "secrecy");
    public static final Identifier HEART_SOUND_ID = Identifier.of("monvhua", "heart");
    public static final Identifier SPEED_MODIFIER_ID = Identifier.of("monvhua", "secrecy_speed_multiplier");
    public static SecrecyItem SECRECY_ITEM;
    public static SoundEvent HEART_SOUND;

    private static final int INFINITE_DURATION = -1;
    private static final int HEART_SOUND_INTERVAL_TICKS = 40;
    private static final float HEART_SOUND_MAX_VOLUME = 1.2F;
    private static final String SILENCED_TAG = "Silenced";
    private static final Map<UUID, Long> VANISH_PENDING_TICKS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> HEART_SOUND_TICKS = new ConcurrentHashMap<>();
    private static final Set<UUID> EXITING_SECRECY = ConcurrentHashMap.newKeySet();
    private static GlobalConfigManager configManager;

    public SecrecyItem(Settings settings) {
        super(settings);
    }

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
            if (!isSecrecyActive(player)) {
                enterSecrecy(player);
                player.sendMessage(Text.literal("§b正在集中精神..."), true);
            }
        } else {
            user.setCurrentHand(hand);
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        if (!world.isClient() && user instanceof ServerPlayerEntity player && !isSecrecyActive(player)) {
            if (!canUseSecrecy(player, true)) {
                player.stopUsingItem();
                return;
            }
            enterSecrecy(player);
            player.sendMessage(Text.literal("§b正在集中精神..."), true);
        }
    }

    @Override
    public boolean onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (!world.isClient() && user instanceof ServerPlayerEntity player) {
            exitSecrecy(player);
        }
        return true;
    }

    @Override
    public int getMaxUseTime(ItemStack stack, LivingEntity user) {
        return 72000;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.BOW;
    }

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
                if (!isSecrecyActive(player)) continue;
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
                if (player != null && hasPreparationEffects(player) && shouldContinueSecrecy(player)) {
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, INFINITE_DURATION, 0, false, false, true));
                    syncSecrecyState(player, true, 0);
                    player.sendMessage(Text.literal("§b精神集中..."), true);
                }
                iterator.remove();
            }
        });
    }

    public static boolean isHoldingSecrecy(ItemStack stack) {
        return SECRECY_ITEM != null && stack.getItem() == SECRECY_ITEM;
    }

    public static int getPlayerStage(ServerPlayerEntity player) {
        if (configManager == null) return 1;
        return com.kuilunfuzhe.monvhua.features.evil_eyes.Evil_Eyes.getPlayerStage(player, configManager);
    }

    public static void exitSecrecy(ServerPlayerEntity player) {
        if (!isSecrecyActive(player)) {
            return;
        }

        VANISH_PENDING_TICKS.remove(player.getUuid());
        HEART_SOUND_TICKS.remove(player.getUuid());
        EXITING_SECRECY.add(player.getUuid());
        removeSpeedModifier(player);
        player.removeStatusEffect(StatusEffects.INVISIBILITY);
        player.removeStatusEffect(StatusEffects.BLINDNESS);
        player.removeStatusEffect(StatusEffects.DARKNESS);
        syncSecrecyState(player, false, 0);
    }

    private static void enterSecrecy(ServerPlayerEntity player) {
        int stage = getPlayerStage(player);
        SecrecyConfig config = SecrecyConfig.getInstance();
        double speedMultiplier = config.getSpeedMultiplier(stage);
        int delaySeconds = config.getVanishDelaySeconds(stage);
        int delayTicks = delaySeconds * 20;

        applySpeedMultiplier(player, speedMultiplier);
        playHeartSound(player);
        EXITING_SECRECY.remove(player.getUuid());
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, INFINITE_DURATION, 0, false, false, false));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, INFINITE_DURATION, 0, false, false, false));
        player.removeStatusEffect(StatusEffects.INVISIBILITY);
        syncSecrecyState(player, false, 0);
        VANISH_PENDING_TICKS.put(player.getUuid(), player.getWorld().getTime() + delayTicks);
    }

    private static boolean isSecrecyActive(ServerPlayerEntity player) {
        return VANISH_PENDING_TICKS.containsKey(player.getUuid())
                || hasSpeedModifier(player)
                || hasPreparationEffects(player)
                || player.hasStatusEffect(StatusEffects.INVISIBILITY);
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
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), HEART_SOUND, SoundCategory.PLAYERS, HEART_SOUND_MAX_VOLUME, 1.0F);
        HEART_SOUND_TICKS.put(uuid, HEART_SOUND_INTERVAL_TICKS);
    }

    private static void syncSecrecyState(ServerPlayerEntity player, boolean invisible, int fadeOutTicks) {
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new SecrecyStateS2CPacket(invisible, Math.max(0, fadeOutTicks)));
    }
}
