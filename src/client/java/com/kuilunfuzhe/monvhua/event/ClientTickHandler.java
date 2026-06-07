package com.kuilunfuzhe.monvhua.event;

import com.kuilunfuzhe.monvhua.features.evil_eyes.Evil_Eyes;
import com.kuilunfuzhe.monvhua.features.mirror.MirrorClientManager;
import com.kuilunfuzhe.monvhua.features.mirror.MirrorViewportRenderer;
import com.kuilunfuzhe.monvhua.features.secrecy.SecrecyClientAudioManager;
import com.kuilunfuzhe.monvhua.gui.action.ActionEditorFragment;
import com.kuilunfuzhe.monvhua.gui.body.bodypose.BodyPoseEditorFragment;
import com.kuilunfuzhe.monvhua.gui.CombinedConfigScreen;
import com.kuilunfuzhe.monvhua.item.gazeguidance.ModItems;
import com.kuilunfuzhe.monvhua.item.mirror.mirror_of_then_and_now;
import com.kuilunfuzhe.monvhua.network.evil_eyes.EvilEyesPackets.*;
import com.kuilunfuzhe.monvhua.network.gazeguidance.MagicPacket;
import com.kuilunfuzhe.monvhua.network.openback.OpenOtherInventoryPayload;
import com.kuilunfuzhe.monvhua.network.openback.PlaceCarriedEntityPayload;
import com.kuilunfuzhe.monvhua.network.mirror.MirrorPackets.ChargeC2S;
import com.kuilunfuzhe.monvhua.network.SafeClientNetworking;
import com.kuilunfuzhe.monvhua.renderer.Font_Render;
import com.kuilunfuzhe.monvhua.renderer.picturerender.AnchorButtonRenderer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
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

public class ClientTickHandler {
    private static boolean hasRequestedConfig = false;
    private static boolean lastMainHandEmpty = true;
    private static boolean lastMirrorRightClick = false;

    public static void register() {
        // Main key handling tick
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            SecrecyClientAudioManager.tick();
            ActionEditorFragment.tickActive();
            if (client.player == null) return;
            if (KeyBindingHandler.configKey.wasPressed() && client.player.isCreative()) {
                client.setScreen(new CombinedConfigScreen());
            }
            if (KeyBindingHandler.bodyPoseEditorKey.wasPressed() && client.player.isCreative()) {
                if (!BodyPoseEditorFragment.tryOpenFromTargetedEditorEntity()) {
                    BodyPoseEditorFragment.open();
                }
            }
            if (KeyBindingHandler.bodyPoseWorldPreviewKey.wasPressed() && client.player.isCreative()) {
                BodyPoseEditorFragment.toggleWorldPreviewModeFromKey();
            }
            if (KeyBindingHandler.actionEditorKey.wasPressed() && client.player.isCreative()) {
                ActionEditorFragment.open();
            }
            if (KeyBindingHandler.markKey.wasPressed()) {
                ItemStack mainHand = client.player.getMainHandStack();

                if (mainHand.getItem() == Evil_Eyes.CLAIRVOYANCE_ITEM) {
                    Vec3d eye = client.player.getEyePos();
                    Vec3d look = client.player.getRotationVec(1.0f);
                    Vec3d end = eye.add(look.multiply(50.0));
                    double blockDist = 50.0;
                    double entityDist = 50.0;
                    Entity entityTarget = null;
                    HitResult hit = client.player.raycast(50.0, 0.0f, false);
                    if (hit.getType() == HitResult.Type.BLOCK) {
                        blockDist = hit.getPos().distanceTo(eye);
                    }
                    for (Entity e : client.world.getEntities()) {
                        if (!(e instanceof LivingEntity) || e == client.player) continue;
                        var boxHit = e.getBoundingBox().raycast(eye, end);
                        if (boxHit.isPresent()) {
                            double d = eye.distanceTo(boxHit.get());
                            if (d < entityDist) { entityDist = d; entityTarget = e; }
                        }
                    }
                    if (entityTarget != null && entityDist <= blockDist) {
                        SafeClientNetworking.send(new MarkEntityC2S(entityTarget.getId()));
                    } else if (hit.getType() == HitResult.Type.BLOCK) {
                        BlockHitResult blockHit = (BlockHitResult) hit;
                        Vec3d pos = blockHit.getPos().add(0, 2.2, 0);
                        SafeClientNetworking.send(new PlaceParrotC2S(pos));
                        for (int i = 0; i < 30; i++)
                            client.particleManager.addParticle(ParticleTypes.ENCHANT, pos.x, pos.y, pos.z, 0, 0.5, 0);
                    } else {
                        client.player.sendMessage(Text.literal("§c请对准实体或方块表面"), true);
                    }
                } else if (mainHand.getItem() == ModItems.MAGIC_STICK) {
                    Entity target = getTargetEntity(client, 50.0);
                    if (target instanceof LivingEntity) {
                        SafeClientNetworking.send(new MagicPacket(target.getId()));
                    }
                } else {
                    if (client.player.isCreative()) {
                        Entity target = getTargetEntity(client, 50.0);
                        if (target instanceof PlayerEntity) {
                            SafeClientNetworking.send(new OpenOtherInventoryPayload(target.getId()));
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

        // Mirror charge right-click detection
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            boolean rightPressed = client.options.useKey.isPressed();
            if (rightPressed != lastMirrorRightClick) {
                lastMirrorRightClick = rightPressed;
                if (client.player.getMainHandStack().getItem() == mirror_of_then_and_now.MIRROR_ITEM) {
                    SafeClientNetworking.send(new ChargeC2S(rightPressed));
                }
            }
        });

        // Request global config on first tick
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null && !hasRequestedConfig) {
                hasRequestedConfig = true;
                SafeClientNetworking.send(new RequestGlobalConfigC2S());
            }
        });

        // Carry entity detection (empty hand → non-empty)
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            boolean currentEmpty = client.player.getMainHandStack().isEmpty();
            if (lastMainHandEmpty && !currentEmpty) {
                SafeClientNetworking.send(new PlaceCarriedEntityPayload());
            }
            lastMainHandEmpty = currentEmpty;
        });

        // Anchor cleanup
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null) {
                SecrecyClientAudioManager.setInvisible(false, 0);
                if (!AnchorButtonRenderer.anchors.isEmpty()) AnchorButtonRenderer.anchors.clear();
                MirrorClientManager.reset();
                MirrorViewportRenderer.cleanup();
                return;
            }
            long now = System.currentTimeMillis();
            AnchorButtonRenderer.anchors.entrySet().removeIf(entry -> now - entry.getValue().lastSeenTime > 3000);
        });

        // Font_Render tick
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Font_Render.tick(client);
        });
    }
}
