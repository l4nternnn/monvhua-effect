package com.kuilunfuzhe.monvhua.event;

import com.kuilunfuzhe.monvhua.features.evil_eyes.Evil_Eyes;
import com.kuilunfuzhe.monvhua.features.mirror.MirrorClientManager;
import com.kuilunfuzhe.monvhua.features.mirror.MirrorViewportRenderer;
import com.kuilunfuzhe.monvhua.features.secrecy.SecrecyClientAudioManager;
import com.kuilunfuzhe.monvhua.gui.CombinedConfigScreen;
import com.kuilunfuzhe.monvhua.item.gazeguidance.ModItems;
import com.kuilunfuzhe.monvhua.item.mirror.mirror_of_then_and_now;
import com.kuilunfuzhe.monvhua.network.evil_eyes.*;
import com.kuilunfuzhe.monvhua.network.gazeguidance.MagicPacket;
import com.kuilunfuzhe.monvhua.network.openback.OpenOtherInventoryPayload;
import com.kuilunfuzhe.monvhua.network.openback.PlaceCarriedEntityPayload;
import com.kuilunfuzhe.monvhua.network.mirror.MirrorChargeC2SPacket;
import com.kuilunfuzhe.monvhua.renderer.FontRender;
import com.kuilunfuzhe.monvhua.renderer.picturerender.AnchorButtonRenderer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

import static com.kuilunfuzhe.monvhua.features.gazeguidance.GazeguidanceClient.getTargetEntity;

/**
 * 客户端每Tick事件处理器，集中注册多个END_CLIENT_TICK阶段的回调。
 * 负责按键响应（标记/交互/配置面板）、镜像充能检测、全局配置请求、
 * 实体搬运检测、锚点清理、字体渲染更新等客户端逻辑。
 */
public class ClientTickHandler {
    /** 是否已经向服务端请求过全局配置（首次进服请求一次） */
    private static boolean hasRequestedConfig = false;
    /** 上一Tick主手是否为空（用于检测从空手切换到非空手的瞬间） */
    private static boolean lastMainHandEmpty = true;
    /** 上一Tick右键是否被按下（用于检测右键按压状态变化） */
    private static boolean lastMirrorRightClick = false;

    public static void register() {
        // ------ 主按键处理：V键标记/交互 ------
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            SecrecyClientAudioManager.tick();
            if (client.player == null || client.world == null) return;
            if (KeyBindingHandler.configKey.wasPressed() && client.player.isCreative()) {
                client.setScreen(new CombinedConfigScreen());
            }
            if (KeyBindingHandler.markKey.wasPressed()) {
                ItemStack mainHand = client.player.getMainHandStack();

                // 手持千里眼道具：标记实体或放置鹦鹉占卜
                if (mainHand.getItem() == Evil_Eyes.CLAIRVOYANCE_ITEM) {
                    Vec3d eye = client.player.getEyePos();
                    Vec3d look = client.player.getRotationVec(1.0f);
                    Vec3d end = eye.add(look.multiply(50.0));
                    // 50格最大射程
                    double blockDist = 50.0;
                    double entityDist = 50.0;
                    Entity entityTarget = null;
                    HitResult hit = client.player.raycast(50.0, 0.0f, false);
                    if (hit.getType() == HitResult.Type.BLOCK) {
                        blockDist = hit.getPos().distanceTo(eye);
                    }
                    // 遍历所有实体，找到视线方向上最近的活体实体
                    for (Entity e : client.world.getEntities()) {
                        if (!(e instanceof LivingEntity) || e == client.player) continue;
                        var boxHit = e.getBoundingBox().raycast(eye, end);
                        if (boxHit.isPresent()) {
                            double d = eye.distanceTo(boxHit.get());
                            if (d < entityDist) { entityDist = d; entityTarget = e; }
                        }
                    }
                    // 优先标记实体，其次在方块上方生成鹦鹉占卜
                    if (entityTarget != null && entityDist <= blockDist) {
                        ClientPlayNetworking.send(new MarkEntityPayload(entityTarget.getId()));
                    } else if (hit.getType() == HitResult.Type.BLOCK) {
                        BlockHitResult blockHit = (BlockHitResult) hit;
                        // 在方块上方2.2格处生成鹦鹉
                        Vec3d pos = blockHit.getPos().add(0, 2.2, 0);
                        ClientPlayNetworking.send(new PlaceParrotC2SPacket(pos));
                        // 生成30个附魔粒子作为视觉反馈
                        for (int i = 0; i < 30; i++)
                            client.particleManager.addParticle(ParticleTypes.ENCHANT, pos.x, pos.y, pos.z, 0, 0.5, 0);
                    } else {
                        client.player.sendMessage(Text.literal("§c请对准实体或方块表面"), true);
                    }
                } else if (mainHand.getItem() == ModItems.MAGIC_STICK) {
                    // 手持凝视法杖：对目标实体施法
                    Entity target = getTargetEntity(client, 50.0);
                    if (target instanceof LivingEntity) {
                        ClientPlayNetworking.send(new MagicPacket(target.getId()));
                    }
                } else {
                    if (client.player.isCreative()) {
                        Entity target = getTargetEntity(client, 50.0);
                        if (target instanceof PlayerEntity) {
                            ClientPlayNetworking.send(new OpenOtherInventoryPayload(target.getId()));
                            client.player.sendMessage(Text.literal("§a尝试打开目标玩家背包"), true);
                        } else {
                            client.player.sendMessage(Text.literal("§c请对准一名玩家"), true);
                        }
                    } else {
                        client.player.sendMessage(Text.literal("§c查看背包功能未完善喵，仅创造模式才能使用此功能"), true);
                    }
                }
            }
        });

        // ------ 镜像充能右键检测 ------
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            boolean rightPressed = client.options.useKey.isPressed();
            if (rightPressed != lastMirrorRightClick) {
                lastMirrorRightClick = rightPressed;
                if (client.player.getMainHandStack().getItem() == mirror_of_then_and_now.MIRROR_ITEM) {
                    ClientPlayNetworking.send(new MirrorChargeC2SPacket(rightPressed));
                }
            }
        });

        // ------ 首次进服请求全局配置（只发一次） ------
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null && !hasRequestedConfig) {
                hasRequestedConfig = true;
                ClientPlayNetworking.send(new RequestGlobalConfigC2SPacket());
            }
        });

        // ------ 实体搬运检测：检测从空手切换到手持物品的瞬间 ------
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            boolean currentEmpty = client.player.getMainHandStack().isEmpty();
            if (lastMainHandEmpty && !currentEmpty) {
                ClientPlayNetworking.send(new PlaceCarriedEntityPayload());
            }
            lastMainHandEmpty = currentEmpty;
        });

        // ------ 锚点超时清理（世界切换时重置所有状态） ------
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null) {
                SecrecyClientAudioManager.setInvisible(false, 0);
                if (!AnchorButtonRenderer.anchors.isEmpty()) AnchorButtonRenderer.anchors.clear();
                MirrorClientManager.reset();
                MirrorViewportRenderer.cleanup();
                return;
            }
            long now = System.currentTimeMillis();
            // 超过3秒未更新的锚点自动移除
            AnchorButtonRenderer.anchors.entrySet().removeIf(entry -> now - entry.getValue().lastSeenTime > 3000);
        });

        // ------ 字体渲染器逐Tick更新 ------
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            FontRender.tick(client);
        });
    }
}
