package com.kuilunfuzhe.monvhua;

import com.kuilunfuzhe.monvhua.event.ClientTickHandler;
import com.kuilunfuzhe.monvhua.event.KeyBindingHandler;
import com.kuilunfuzhe.monvhua.event.WorldRenderHandler;
import com.kuilunfuzhe.monvhua.compat.DhCompat;
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

/**
 * 模组客户端入口，负责所有客户端初始化逻辑。
 * <p>
 * 核心职责包括：注册S2C网络包接收器、初始化客户端模块（千里眼/凝视引导/镜面HUD等）、
 * 注册按键绑定、注册World渲染回调、注册交互事件（攻击锚点装甲架、搬运实体、放置搬运实体）、
 * 注册自定义屏幕处理。
 * </p>
 */
public class MonvhuaModClient implements ClientModInitializer {
    /** 客户端当前玩家的千里眼阶段（默认1），由服务端同步更新 */
    public static int currentPlayerStage = 1;

    /**
     * 客户端初始化入口。
     * <p>初始化顺序：
     * <ol>
     *   <li>注册S2C网络包接收器</li>
     *   <li>初始化各功能客户端模块</li>
     *   <li>注册按键、Tick、网络包、World渲染等处理器</li>
     *   <li>注册交互事件回调（攻击锚点、搬运实体等）</li>
     *   <li>注册自定义GUI屏幕</li>
     * </ol>
     * </p>
     */
    @Override
    public void onInitializeClient() {
        // ===== 1. 网络包接收器注册 =====
        ModNetworking.registerS2CPackets();

        // ===== 2. 功能模块客户端初始化 =====
        GazeguidanceClient.initialize();
        DhCompat.init(); // 远处地平线兼容初始化

        // ===== 3. 客户端处理器注册 =====
        KeyBindingHandler.register();
        ClientTickHandler.register();
        ClientPacketHandler.register();
        WorldRenderHandler.register();
        mirrorHUD.register();
        BodyBlockModelRegister.register();

        // ===== 4. 摄像机追踪与镜面HUD =====
        ClientCameraWatchReceiver.register();
        CameraWatchClientHandler.initialize();
        MirrorHudOverlay.register();

        // ===== 5. 攻击锚点装甲架 =====
        // 当玩家攻击名为 "clairvoyance_evil_eyes" 的盔甲架时，发送锚点破坏包
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (entity instanceof ArmorStandEntity armorStand) {
                Text name = armorStand.getCustomName();
                if (name != null && name.getString().equals("clairvoyance_evil_eyes")) {
                    ClientPlayNetworking.send(new AnchorDestroyC2SPacket(armorStand.getUuid()));
                    player.swingHand(hand); // 客户端侧挥手动效
                    player.sendMessage(Text.literal("§a锚点已破坏"), true);
                    return ActionResult.FAIL; // 取消原攻击逻辑
                }
            }
            return ActionResult.PASS;
        });

        // ===== 6. 注册自定义屏幕 =====
        // 他人背包屏幕
        HandledScreens.register(MonvhuaMod.OTHER_INVENTORY_HANDLER, OtherPlayerInventoryScreen::new);

        // ===== 7. 搬运实体交互事件 =====
        // 潜行 + 空手右键方块：放置搬运中的实体
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (player instanceof ClientPlayerEntity clientPlayer && clientPlayer.isSneaking() &&
                    clientPlayer.getMainHandStack().isEmpty() && clientPlayer.getOffHandStack().isEmpty()) {
                ClientPlayNetworking.send(new PlaceCarriedEntityPayload());
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });

        // 主手空手使用物品：放置搬运中的实体
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (player instanceof ClientPlayerEntity clientPlayer && hand == Hand.MAIN_HAND) {
                if (clientPlayer.getMainHandStack().isEmpty() && clientPlayer.getOffHandStack().isEmpty()) {
                    ClientPlayNetworking.send(new PlaceCarriedEntityPayload());
                }
            }
            return ActionResult.PASS;
        });

        // 空手右键生物：抓取实体开始搬运
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

        // 打开物品栏屏幕时：尝试放置搬运中的实体
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof net.minecraft.client.gui.screen.ingame.InventoryScreen) {
                ClientPlayNetworking.send(new PlaceCarriedEntityPayload());
            }
        });

        // 肢体部件屏幕
        HandledScreens.register(ModScreenHandlers.BODY_PART_SCREEN_HANDLER, BodyPartScreen::new);
    }
}
