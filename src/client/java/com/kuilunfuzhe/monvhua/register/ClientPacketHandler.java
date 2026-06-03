package com.kuilunfuzhe.monvhua.register;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kuilunfuzhe.monvhua.MonvhuaModClient;
import com.kuilunfuzhe.monvhua.features.evil_eyes.Evil_Eyes;
import com.kuilunfuzhe.monvhua.features.evil_eyes.Evil_EyesClient;
import com.kuilunfuzhe.monvhua.features.evil_eyes.watch.CameraWatchClientHandler;
import com.kuilunfuzhe.monvhua.features.gazeguidance.GazeguidanceClient;
import com.kuilunfuzhe.monvhua.features.secrecy.SecrecyClientAudioManager;
import com.kuilunfuzhe.monvhua.features.mirror.MirrorClientManager;
import com.kuilunfuzhe.monvhua.features.carryentity.CarryPoseClientState;
import com.kuilunfuzhe.monvhua.features.action.ActionConfig;
import com.kuilunfuzhe.monvhua.features.action.TimelineClientState;
import com.kuilunfuzhe.monvhua.gui.CombinedConfigScreen;
import com.kuilunfuzhe.monvhua.gui.action.ActionEditorFragment;
import com.kuilunfuzhe.monvhua.item.config.GazeConfig;
import com.kuilunfuzhe.monvhua.item.config.MirrorConfig;
import com.kuilunfuzhe.monvhua.item.config.SecrecyConfig;
import com.kuilunfuzhe.monvhua.network.evil_eyes.*;
import com.kuilunfuzhe.monvhua.network.floating.FullWitchTagSyncS2CPacket;
import com.kuilunfuzhe.monvhua.network.gazeguidance.*;
import com.kuilunfuzhe.monvhua.network.mirror.MirrorChargeSyncS2CPacket;
import com.kuilunfuzhe.monvhua.network.mirror.MirrorConfigS2CPacket;
import com.kuilunfuzhe.monvhua.network.mirror.MirrorStateS2CPacket;
import com.kuilunfuzhe.monvhua.network.secrecy.SecrecyConfigS2CPacket;
import com.kuilunfuzhe.monvhua.network.secrecy.SecrecyStateS2CPacket;
import com.kuilunfuzhe.monvhua.network.carryentity.CarryPoseSyncS2CPacket;
import com.kuilunfuzhe.monvhua.network.action.*;
import com.kuilunfuzhe.monvhua.renderer.picturerender.AnchorButtonRenderer;
import com.kuilunfuzhe.monvhua.renderer.picturerender.BackTextureRenderer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

/**
 * 客户端网络包处理器注册中心。
 * 注册所有 S2C 包的回调，涵盖千里眼、视线诱导、镜像、隐秘等系统的客户端状态同步。
 */
public class ClientPacketHandler {
    private static volatile int lastNotifiedStage = 1;

    /**
     * 注册所有 S2C 网络包的客户端处理回调。
     * 每个回调在客户端主线程执行，对接收到的数据进行解析和状态更新。
     */
    public static void register() {
        // 1. 千里眼全局配置接收
        ClientPlayNetworking.registerGlobalReceiver(GlobalConfigS2CPacket.ID, (packet, context) -> {
            context.client().execute(() -> {
                try {
                    JsonObject root = JsonParser.parseString(packet.json()).getAsJsonObject();
                    GlobalConfigS2CPacket.StageConfig[] configs = new GlobalConfigS2CPacket.StageConfig[7];
                    for (int i = 1; i <= 7; i++) {
                        JsonObject stageObj = root.getAsJsonObject("stage" + i);
                        configs[i - 1] = new GlobalConfigS2CPacket.StageConfig(
                                stageObj.get("dailyLimit").getAsInt(),
                                stageObj.get("maxMarks").getAsInt(),
                                stageObj.get("minScore").getAsInt(),
                                stageObj.get("maxScore").getAsInt(),
                                stageObj.get("watchRequiredTicks").getAsInt(),
                                stageObj.get("parrotDailyLimit").getAsInt(),
                                stageObj.get("maxActiveParrots").getAsInt()
                        );
                    }
                    if (context.client().currentScreen instanceof CombinedConfigScreen screen) {
                        screen.receiveEvilConfigs(configs);
                    }
                } catch (Exception e) { e.printStackTrace(); }
            });
        });

        // 2. 打开/关闭 UI（旧系统）
        ClientPlayNetworking.registerGlobalReceiver(OpenUIPacket.ID, (packet, context) -> {
            context.client().execute(() -> {
                if (context.client().currentScreen instanceof com.kuilunfuzhe.monvhua.gui.evil_eyes.Evil_eyesScreen) context.client().setScreen(null);
                else context.client().setScreen(new com.kuilunfuzhe.monvhua.gui.evil_eyes.Evil_eyesScreen());
            });
        });

        // 3. 实体标记更新
        ClientPlayNetworking.registerGlobalReceiver(EntityMarkedPayload.ID, (packet, context) -> {
            context.client().execute(() -> {
                UUID uuid = packet.entityUuid();
                if (uuid.getMostSignificantBits() == 0 && uuid.getLeastSignificantBits() == 0) {
                    Evil_EyesClient.localMarkedEntities.clear();
                    com.kuilunfuzhe.monvhua.gui.evil_eyes.Evil_eyesScreen.updateMarkedList(Evil_EyesClient.localMarkedEntities);
                    return;
                }
                long expire = context.client().world != null ? context.client().world.getTime() + 60 : System.currentTimeMillis() / 50 + 60;
                Evil_EyesClient.localMarkedEntities.put(uuid, expire);
                com.kuilunfuzhe.monvhua.gui.evil_eyes.Evil_eyesScreen.updateMarkedList(Evil_EyesClient.localMarkedEntities);
            });
        });

        // 4. 切换图片显示
        ClientPlayNetworking.registerGlobalReceiver(ToggleImagesS2CPacket.ID, (packet, context) -> {
            context.client().execute(() -> BackTextureRenderer.imagesEnabled = packet.enabled());
        });

        // 5. 选择观看
        ClientPlayNetworking.registerGlobalReceiver(SelectViewPayload.ID, (packet, context) -> {
            context.client().execute(() -> {
                CameraWatchClientHandler.onUnbind();
                Evil_EyesClient.onSelectView(packet.entityUuid());
            });
        });

        // 6. 强制退出观看（旧系统）
        ClientPlayNetworking.registerGlobalReceiver(ForceExitViewPayload.ID, (packet, context) -> {
            context.client().execute(() -> {
                CameraWatchClientHandler.onUnbind();
                Evil_EyesClient.exitViewMode(context.client());
            });
        });

        // 7. 标记粒子效果
        ClientPlayNetworking.registerGlobalReceiver(MarkParticleS2CPacket.ID, (packet, context) -> {
            context.client().execute(() -> {
                Vec3d pos = packet.pos();
                if (context.client().world != null) {
                    context.client().particleManager.addParticle(ParticleTypes.ENCHANT, pos.x, pos.y, pos.z, 0, 0.5, 0);
                }
            });
        });

        // 8. 视线诱导配置同步
        ClientPlayNetworking.registerGlobalReceiver(SyncConfigS2CPacket.ID, (packet, context) -> {
            context.client().execute(() -> {
                GazeConfig config = GazeConfig.fromJson(packet.json());
                if (context.client().currentScreen instanceof CombinedConfigScreen screen) {
                    screen.receiveGazeConfig(config);
                }
            });
        });

        // mirror config sync S2C
        ClientPlayNetworking.registerGlobalReceiver(MirrorConfigS2CPacket.ID, (packet, context) -> {
            context.client().execute(() -> {
                MirrorConfig config = MirrorConfig.fromJson(packet.json());
                if (context.client().currentScreen instanceof CombinedConfigScreen screen) {
                    screen.receiveMirrorConfig(config);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(SecrecyConfigS2CPacket.ID, (packet, context) -> {
            context.client().execute(() -> {
                SecrecyConfig config = SecrecyConfig.fromJson(packet.json());
                if (context.client().currentScreen instanceof CombinedConfigScreen screen) {
                    screen.receiveSecrecyConfig(config);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(SecrecyStateS2CPacket.ID, (packet, context) -> {
            context.client().execute(() -> SecrecyClientAudioManager.setInvisible(packet.invisible(), packet.fadeOutTicks()));
        });

        // 9. 能量同步
        ClientPlayNetworking.registerGlobalReceiver(EnergySyncPacket.ID, (packet, context) -> {
            context.client().execute(() -> GazeguidanceClient.setEnergy(packet.currentEnergy(), packet.maxEnergy()));
        });

        // 漂浮：同步服务端玩家标签状态
        ClientPlayNetworking.registerGlobalReceiver(FullWitchTagSyncS2CPacket.ID, (packet, context) -> {
            context.client().execute(() -> com.kuilunfuzhe.monvhua.features.floating.floating.syncFullWitchTag(packet.hasFullWitchTag()));
        });

        // 10. 标记数量同步
        ClientPlayNetworking.registerGlobalReceiver(MarkCountPacket.ID, (packet, context) -> {
            context.client().execute(() -> GazeguidanceClient.setMarkCount(packet.count()));
        });

        // 11. 粒子效果（静态点）
        ClientPlayNetworking.registerGlobalReceiver(ParticlePacket.ID, (packet, context) -> {
            context.client().execute(() -> {
                if (context.client().world != null) {
                    Vec3d pos = new Vec3d(packet.x(), packet.y(), packet.z());
                    for (int i = 0; i < 20; i++) {
                        context.client().particleManager.addParticle(ParticleTypes.END_ROD, pos.x, pos.y + 0.5, pos.z, 0.5, 0.5, 0.5);
                    }
                }
            });
        });

        // 12. 强度阶段同步
        ClientPlayNetworking.registerGlobalReceiver(StrengthPacket.ID, (packet, context) -> {
            context.client().execute(() -> GazeguidanceClient.setStrength(packet.stage(), packet.maxMarks()));
        });

        // 13. 锚点粒子接收（同时缓存位置）
        ClientPlayNetworking.registerGlobalReceiver(AnchorParticleS2CPacket.ID, (packet, context) -> {
            context.client().execute(() -> {
                MinecraftClient client = context.client();
                if (client.world == null) return;
                Vec3d pos = packet.pos();
                if (packet.type() == 0) {
                    client.particleManager.addParticle(ParticleTypes.PORTAL, pos.x, pos.y, pos.z, 0, 0.1, 0);
                } else {
                    client.particleManager.addParticle(ParticleTypes.WHITE_SMOKE, pos.x, pos.y, pos.z, 0, 0.05, 0);
                }
                UUID standId = packet.standId();
                AnchorButtonRenderer.AnchorInfo info = AnchorButtonRenderer.anchors.get(standId);
                if (info == null) {
                    info = new AnchorButtonRenderer.AnchorInfo();
                    AnchorButtonRenderer.anchors.put(standId, info);
                }
                info.pos = pos;
                info.lastSeenTime = System.currentTimeMillis();
            });
        });

        // 14. 玩家阶段同步
        ClientPlayNetworking.registerGlobalReceiver(PlayerStageS2CPacket.ID, (packet, context) -> {
            context.client().execute(() -> {
                int newStage = packet.stage();
                MonvhuaModClient.currentPlayerStage = newStage;
                MinecraftClient client = context.client();
                if (client.player != null) {
                    ItemStack mainHand = client.player.getMainHandStack();
                    if (mainHand.getItem() == Evil_Eyes.CLAIRVOYANCE_ITEM) {
                        if (newStage != lastNotifiedStage) {
                            lastNotifiedStage = newStage;
                            showStageUpgradeToast(newStage);
                        }
                    }
                }
            });
        });

        // 15. 爆炸粒子接收
        ClientPlayNetworking.registerGlobalReceiver(ExplosionParticleS2CPacket.ID, (packet, context) -> {
            context.client().execute(() -> {
                MinecraftClient client = context.client();
                if (client.world == null) return;
                Vec3d pos = packet.pos();
                for (int i = 0; i < 20; i++) {
                    double ox = (client.world.random.nextDouble() - 0.5) * 1.5;
                    double oy = (client.world.random.nextDouble() - 0.5) * 1.5;
                    double oz = (client.world.random.nextDouble() - 0.5) * 1.5;
                    client.particleManager.addParticle(ParticleTypes.EXPLOSION, pos.x + ox, pos.y + oy, pos.z + oz, 0, 0, 0);
                    client.particleManager.addParticle(ParticleTypes.POOF, pos.x + ox, pos.y + oy, pos.z + oz, 0, 0, 0);
                }
            });
        });

        // 镜像视图 S2C 接收
        ClientPlayNetworking.registerGlobalReceiver(MirrorStateS2CPacket.ID, (packet, context) -> {
            context.client().execute(() -> MirrorClientManager.onStatePacket(packet));
        });

        // 镜像充能同步接收
        ClientPlayNetworking.registerGlobalReceiver(MirrorChargeSyncS2CPacket.ID, (packet, context) -> {
            context.client().execute(() -> MirrorClientManager.setCharge(packet.currentTicks(), packet.maxTicks()));
        });

        ClientPlayNetworking.registerGlobalReceiver(CarryPoseSyncS2CPacket.ID, (packet, context) -> {
            context.client().execute(() -> CarryPoseClientState.apply(packet));
        });

        ClientPlayNetworking.registerGlobalReceiver(ActionsConfigS2CPacket.ID, (packet, context) -> {
            context.client().execute(() -> {
                ActionEditorFragment inst = ActionEditorFragment.activeInstance;
                if (inst != null) inst.receiveConfig(ActionConfig.fromJson(packet.json()));
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ActionFilesListS2CPacket.ID, (packet, context) -> {
            context.client().execute(() -> {
                ActionEditorFragment inst = ActionEditorFragment.activeInstance;
                if (inst != null) inst.receiveFileList(packet.files());
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(PreviewResultS2CPacket.ID, (packet, context) -> {
            context.client().execute(() -> {
                ActionEditorFragment inst = ActionEditorFragment.activeInstance;
                if (inst != null) inst.receivePreviewResult(packet.actionId(), packet.previewText());
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(PreviewTimelineResultS2CPacket.ID, (packet, context) -> {
            context.client().execute(() -> {
                ActionEditorFragment inst = ActionEditorFragment.activeInstance;
                if (inst != null) inst.receiveTimelinePreviewResult(packet.entries());
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(TimelineStateS2CPacket.ID, (packet, context) -> {
            context.client().execute(() -> {
                TimelineClientState.currentSecond = packet.currentSecond();
                TimelineClientState.running = packet.running();
                TimelineClientState.paused = packet.paused();
                TimelineClientState.loop = packet.loop();
                TimelineClientState.totalSeconds = packet.totalSeconds();
                ActionEditorFragment.tickActive();
            });
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            CarryPoseClientState.clear();
            com.kuilunfuzhe.monvhua.features.floating.floating.syncFullWitchTag(false);
        });
    }

    /**
     * 弹出阶段升级提示 Toast。
     * @param newStage 新的阶段编号
     */
    private static void showStageUpgradeToast(int newStage) {
        MinecraftClient client = MinecraftClient.getInstance();
        SystemToast.show(client.getToastManager(), SystemToast.Type.NARRATOR_TOGGLE, Text.literal("§6千里眼"), Text.literal("阶段提升至 §a" + newStage));
    }
}
