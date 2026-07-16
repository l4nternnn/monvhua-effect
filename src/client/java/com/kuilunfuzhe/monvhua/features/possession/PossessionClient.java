package com.kuilunfuzhe.monvhua.features.possession;

import com.kuilunfuzhe.monvhua.network.SafeClientNetworking;
import com.kuilunfuzhe.monvhua.features.possession.PossessionFeature;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.MathHelper;

import java.util.Arrays;
import java.util.UUID;

public final class PossessionClient {
    private static final int OFF_HAND_INVENTORY_SLOT = 40;
    private static boolean active = false;
    private static int targetEntityId = -1;
    private static UUID targetUuid = new UUID(0L, 0L);
    private static boolean lastUse = false;
    private static final ItemStack[] targetHotbar = new ItemStack[9];
    private static final ItemStack[] targetInventory = new ItemStack[PossessionPackets.InventoryS2C.MAX_SLOTS];
    private static int targetSelectedSlot = 0;
    private static final int LOOK_INTERPOLATION_FRAMES = 2;
    private static boolean lookInitialized = false;
    private static float renderedYaw;
    private static float renderedPitch;
    private static float lookStartYaw;
    private static float lookStartPitch;
    private static float lookTargetYaw;
    private static float lookTargetPitch;
    private static int lookInterpolationFrame;

    private PossessionClient() {
    }

    public static void initialize() {
        Arrays.fill(targetHotbar, ItemStack.EMPTY);
        Arrays.fill(targetInventory, ItemStack.EMPTY);
        ClientPlayNetworking.registerGlobalReceiver(PossessionPackets.StateS2C.ID, (packet, context) ->
                context.client().execute(() -> applyState(context.client(), packet)));
        ClientPlayNetworking.registerGlobalReceiver(PossessionPackets.HotbarS2C.ID, (packet, context) ->
                context.client().execute(() -> applyHotbar(packet)));
        ClientPlayNetworking.registerGlobalReceiver(PossessionPackets.InventoryS2C.ID, (packet, context) ->
                context.client().execute(() -> applyInventory(packet)));

        ClientTickEvents.END_CLIENT_TICK.register(PossessionClient::tick);
        PossessionHotbarHud.register();
    }

    public static boolean isActive() {
        return active;
    }

    public static Entity getTargetEntity(MinecraftClient client) {
        return client.world == null || targetEntityId < 0 ? null : client.world.getEntityById(targetEntityId);
    }

    public static void prepareRenderView(MinecraftClient client) {
        if (!active || client.player == null) {
            return;
        }
        Entity target = getTargetEntity(client);
        if (target == null) {
            return;
        }

        float wantedYaw = client.player.getYaw();
        float wantedPitch = client.player.getPitch();
        if (!lookInitialized) {
            renderedYaw = wantedYaw;
            renderedPitch = wantedPitch;
            lookTargetYaw = wantedYaw;
            lookTargetPitch = wantedPitch;
            lookInitialized = true;
            lookInterpolationFrame = LOOK_INTERPOLATION_FRAMES;
        } else if (wantedYaw != lookTargetYaw || wantedPitch != lookTargetPitch) {
            lookStartYaw = renderedYaw;
            lookStartPitch = renderedPitch;
            lookTargetYaw = wantedYaw;
            lookTargetPitch = wantedPitch;
            lookInterpolationFrame = 0;
        }

        if (lookInterpolationFrame < LOOK_INTERPOLATION_FRAMES) {
            lookInterpolationFrame++;
            float progress = lookInterpolationFrame / (float) LOOK_INTERPOLATION_FRAMES;
            renderedYaw = MathHelper.lerpAngleDegrees(progress, lookStartYaw, lookTargetYaw);
            renderedPitch = MathHelper.lerp(progress, lookStartPitch, lookTargetPitch);
        }

        target.lastYaw = renderedYaw;
        target.lastPitch = renderedPitch;
        target.setYaw(renderedYaw);
        target.setPitch(renderedPitch);
        target.setHeadYaw(renderedYaw);
    }

    public static ItemStack getTargetHotbarStack(int slot) {
        if (slot < 0 || slot >= targetHotbar.length) {
            return ItemStack.EMPTY;
        }
        return targetHotbar[slot];
    }

    public static int getTargetSelectedSlot() {
        return targetSelectedSlot;
    }

    public static ItemStack getTargetInventoryStack(int slot) {
        if (slot < 0 || slot >= targetInventory.length) {
            return ItemStack.EMPTY;
        }
        return targetInventory[slot];
    }

    public static boolean shouldMirrorHandsFor(Object entity) {
        MinecraftClient client = MinecraftClient.getInstance();
        return active && client.player != null && entity == client.player;
    }

    public static ItemStack getMirroredMainHandStack() {
        return getTargetHotbarStack(targetSelectedSlot);
    }

    public static ItemStack getMirroredOffHandStack() {
        return getTargetInventoryStack(OFF_HAND_INVENTORY_SLOT);
    }

    private static void applyState(MinecraftClient client, PossessionPackets.StateS2C packet) {
        active = packet.active();
        targetEntityId = packet.targetEntityId();
        targetUuid = packet.targetUuid();
        lastUse = active && client.options.useKey.isPressed();
        lookInitialized = false;
        if (!active && client.player != null) {
            client.cameraEntity = client.player;
            if (client.currentScreen instanceof PossessionInventoryScreen) {
                client.setScreen(null);
            }
            clearHotbar();
            clearInventory();
        }
    }

    private static void applyHotbar(PossessionPackets.HotbarS2C packet) {
        targetSelectedSlot = packet.selectedSlot();
        for (int i = 0; i < targetHotbar.length; i++) {
            targetHotbar[i] = i < packet.hotbar().size() ? packet.hotbar().get(i).copy() : ItemStack.EMPTY;
        }
    }

    private static void clearHotbar() {
        Arrays.fill(targetHotbar, ItemStack.EMPTY);
        targetSelectedSlot = 0;
    }

    private static void applyInventory(PossessionPackets.InventoryS2C packet) {
        targetSelectedSlot = packet.selectedSlot();
        for (int i = 0; i < targetInventory.length; i++) {
            targetInventory[i] = i < packet.stacks().size() ? packet.stacks().get(i).copy() : ItemStack.EMPTY;
        }
    }

    private static void clearInventory() {
        Arrays.fill(targetInventory, ItemStack.EMPTY);
    }

    private static void tick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            active = false;
            targetEntityId = -1;
            return;
        }
        if (!active) {
            return;
        }

        Entity target = getTargetEntity(client);
        if (target != null && client.cameraEntity != target) {
            client.cameraEntity = target;
        }
        if (target != null) {
            target.setYaw(client.player.getYaw());
            target.setPitch(client.player.getPitch());
            target.setHeadYaw(client.player.getYaw());
        }

        while (client.options.inventoryKey.wasPressed()) {
            if (client.currentScreen == null) {
                client.setScreen(new PossessionInventoryScreen());
            }
        }

        PlayerInput input = new PlayerInput(
                client.options.forwardKey.isPressed(),
                client.options.backKey.isPressed(),
                client.options.leftKey.isPressed(),
                client.options.rightKey.isPressed(),
                client.options.jumpKey.isPressed(),
                client.options.sneakKey.isPressed(),
                client.options.sprintKey.isPressed()
        );
        SafeClientNetworking.send(new PossessionPackets.InputC2S(
                input,
                client.player.getYaw(),
                client.player.getPitch(),
                client.player.getInventory().getSelectedSlot()
        ));

        boolean use = client.options.useKey.isPressed();
        if (use && !lastUse && isHoldingActualPossessionItem(client)) {
            SafeClientNetworking.send(new PossessionPackets.StopC2S());
        } else if (!use && lastUse) {
            SafeClientNetworking.send(new PossessionPackets.ActionC2S(PossessionPackets.ActionC2S.RELEASE_USE));
        }
        lastUse = use;
    }

    public static boolean isHoldingActualPossessionItem(MinecraftClient client) {
        if (client.player == null) {
            return false;
        }
        int selectedSlot = client.player.getInventory().getSelectedSlot();
        boolean mainHand = selectedSlot >= 0
                && selectedSlot < client.player.getInventory().size()
                && client.player.getInventory().getStack(selectedSlot).isOf(PossessionFeature.POSSESSION_ITEM);
        boolean offHand = OFF_HAND_INVENTORY_SLOT < client.player.getInventory().size()
                && client.player.getInventory().getStack(OFF_HAND_INVENTORY_SLOT).isOf(PossessionFeature.POSSESSION_ITEM);
        return mainHand || offHand;
    }
}
