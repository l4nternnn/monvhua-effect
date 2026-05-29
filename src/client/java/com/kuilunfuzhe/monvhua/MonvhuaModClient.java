package com.kuilunfuzhe.monvhua;

import com.kuilunfuzhe.monvhua.event.ClientTickHandler;
import com.kuilunfuzhe.monvhua.event.KeyBindingHandler;
import com.kuilunfuzhe.monvhua.event.WorldRenderHandler;
import com.kuilunfuzhe.monvhua.features.evil_eyes.Evil_EyesClient;
import com.kuilunfuzhe.monvhua.features.evil_eyes.watch.CameraWatchClientHandler;
import com.kuilunfuzhe.monvhua.features.evil_eyes.watch.ClientCameraWatchReceiver;
import com.kuilunfuzhe.monvhua.features.gazeguidance.GazeguidanceClient;
import com.kuilunfuzhe.monvhua.features.mirror.MirrorHudOverlay;
import com.kuilunfuzhe.monvhua.gui.bodyback.BodyPartScreen;
import com.kuilunfuzhe.monvhua.gui.mirror.mirrorHUD;
import com.kuilunfuzhe.monvhua.gui.openback.OtherPlayerInventoryScreen;
import com.kuilunfuzhe.monvhua.network.ModNetworking;
import com.kuilunfuzhe.monvhua.network.evil_eyes.AnchorDestroyC2SPacket;
import com.kuilunfuzhe.monvhua.network.openback.CarryEntityPayload;
import com.kuilunfuzhe.monvhua.network.openback.PlaceCarriedEntityPayload;
import com.kuilunfuzhe.monvhua.register.BodyBlockModelRegister;
import com.kuilunfuzhe.monvhua.register.ClientPacketHandler;
import com.kuilunfuzhe.monvhua.screen.ModScreenHandlers;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

public class MonvhuaModClient implements ClientModInitializer {
    public static int currentPlayerStage = 1;

    @Override
    public void onInitializeClient() {
        ModNetworking.registerS2CPackets();

        Evil_EyesClient.initialize();
        GazeguidanceClient.initialize();

        KeyBindingHandler.register();
        ClientTickHandler.register();
        ClientPacketHandler.register();
        WorldRenderHandler.register();
        mirrorHUD.register();
        BodyBlockModelRegister.register();

        ClientCameraWatchReceiver.register();
        CameraWatchClientHandler.initialize();
        MirrorHudOverlay.register();

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (entity instanceof ArmorStandEntity armorStand) {
                Text name = armorStand.getCustomName();
                if (name != null && name.getString().equals("clairvoyance_evil_eyes")) {
                    ClientPlayNetworking.send(new AnchorDestroyC2SPacket(armorStand.getUuid()));
                    player.swingHand(hand);
                    player.sendMessage(Text.literal("§a锚点已破坏"), true);
                    return ActionResult.FAIL;
                }
            }
            return ActionResult.PASS;
        });

        HandledScreens.register(MonvhuaMod.OTHER_INVENTORY_HANDLER, OtherPlayerInventoryScreen::new);

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (player instanceof ClientPlayerEntity clientPlayer && clientPlayer.isSneaking() &&
                    clientPlayer.getMainHandStack().isEmpty() && clientPlayer.getOffHandStack().isEmpty()) {
                ClientPlayNetworking.send(new PlaceCarriedEntityPayload());
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (player instanceof ClientPlayerEntity clientPlayer && hand == Hand.MAIN_HAND) {
                if (clientPlayer.getMainHandStack().isEmpty() && clientPlayer.getOffHandStack().isEmpty()) {
                    ClientPlayNetworking.send(new PlaceCarriedEntityPayload());
                }
            }
            return ActionResult.PASS;
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (player instanceof ClientPlayerEntity clientPlayer &&
                    clientPlayer.getMainHandStack().isEmpty() && clientPlayer.getOffHandStack().isEmpty()) {
                if (entity instanceof LivingEntity) {
                    ClientPlayNetworking.send(new CarryEntityPayload(entity.getId()));
                    return ActionResult.SUCCESS;
                }
            }
            return ActionResult.PASS;
        });

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof net.minecraft.client.gui.screen.ingame.InventoryScreen) {
                ClientPlayNetworking.send(new PlaceCarriedEntityPayload());
            }
        });

        HandledScreens.register(ModScreenHandlers.BODY_PART_SCREEN_HANDLER, BodyPartScreen::new);
    }
}
