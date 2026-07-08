package com.kuilunfuzhe.monvhua.register;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kuilunfuzhe.monvhua.event.tag_pitch;
import com.kuilunfuzhe.monvhua.MonvhuaModClient;
import com.kuilunfuzhe.monvhua.features.evil_eyes.ClairvoyanceEnergyClient;
import com.kuilunfuzhe.monvhua.features.evil_eyes.Evil_Eyes;
import com.kuilunfuzhe.monvhua.features.evil_eyes.Evil_EyesClient;
import com.kuilunfuzhe.monvhua.features.evil_eyes.watch.CameraWatchClientHandler;
import com.kuilunfuzhe.monvhua.features.gazeguidance.GazeguidanceClient;
import com.kuilunfuzhe.monvhua.features.gravity.GravityClient;
import com.kuilunfuzhe.monvhua.features.hold_hands.HoldHandsClientState;
import com.kuilunfuzhe.monvhua.features.through.ThroughClientManager;
import com.kuilunfuzhe.monvhua.features.mirror.MirrorClientManager;
import com.kuilunfuzhe.monvhua.features.carryentity.CarryPoseTuning;
import com.kuilunfuzhe.monvhua.features.carryentity.CarryTransformConfig;
import com.kuilunfuzhe.monvhua.features.carryentity.CarryPoseClientState;
import com.kuilunfuzhe.monvhua.features.action.ActionConfig;
import com.kuilunfuzhe.monvhua.features.action.TimelineClientState;
import com.kuilunfuzhe.monvhua.features.action.ActionPoseClientState;
import com.kuilunfuzhe.monvhua.gui.CombinedConfigScreen;
import com.kuilunfuzhe.monvhua.gui.action.ActionEditorFragment;
import com.kuilunfuzhe.monvhua.item.config.GazeConfig;
import com.kuilunfuzhe.monvhua.item.config.FloatingConfig;
import com.kuilunfuzhe.monvhua.item.config.MirrorConfig;
import com.kuilunfuzhe.monvhua.item.config.PlantMagicConfig;
import com.kuilunfuzhe.monvhua.item.config.ThroughConfig;
import com.kuilunfuzhe.monvhua.item.gravity.GravityItems;
import com.kuilunfuzhe.monvhua.item.imitate.ImitateItem;
import com.kuilunfuzhe.monvhua.item.mirror.mirror_of_then_and_now;
import com.kuilunfuzhe.monvhua.item.plant.PlantMagicItems;
import com.kuilunfuzhe.monvhua.item.through.ThroughItem;
import com.kuilunfuzhe.monvhua.network.evil_eyes.EvilEyesPackets.*;
import com.kuilunfuzhe.monvhua.network.floating.FullWitchTagSyncS2CPacket;
import com.kuilunfuzhe.monvhua.network.floating.FloatingPackets;
import com.kuilunfuzhe.monvhua.network.gazeguidance.*;
import com.kuilunfuzhe.monvhua.network.general_stage.GeneralStagePackets.*;
import com.kuilunfuzhe.monvhua.network.mirror.MirrorPackets.ChargeSyncS2C;
import com.kuilunfuzhe.monvhua.network.mirror.MirrorPackets.ConfigS2C;
import com.kuilunfuzhe.monvhua.network.plant.PlantMagicPackets;
import com.kuilunfuzhe.monvhua.network.mirror.MirrorPackets.StateS2C;
import com.kuilunfuzhe.monvhua.network.through.ThroughConfigS2CPacket;
import com.kuilunfuzhe.monvhua.network.through.ThroughStateS2CPacket;
import com.kuilunfuzhe.monvhua.network.carryentity.CarryPoseSyncS2CPacket;
import com.kuilunfuzhe.monvhua.network.carryentity.CarryTransformPackets;
import com.kuilunfuzhe.monvhua.network.hold_hands.HoldHandsSyncS2CPacket;
import com.kuilunfuzhe.monvhua.network.action.ActionPackets.*;
import com.kuilunfuzhe.monvhua.renderer.picturerender.AnchorButtonRenderer;
import com.kuilunfuzhe.monvhua.renderer.picturerender.BackTextureRenderer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.item.Item;
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

    private static void showStageUpgradeToast(String magicName, int newStage) {
        MinecraftClient client = MinecraftClient.getInstance();
        SystemToast.show(client.getToastManager(), SystemToast.Type.NARRATOR_TOGGLE,
                Text.literal("§6" + magicName),
                Text.literal("阶段 §a" + newStage));
    }

    private static String currentStageToastMagicName(MinecraftClient client) {
        if (client == null || client.player == null) {
            return null;
        }
        String main = stageToastMagicName(client.player.getMainHandStack());
        if (main != null) {
            return main;
        }
        return stageToastMagicName(client.player.getOffHandStack());
    }

    private static String stageToastMagicName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        Item item = stack.getItem();
        if (item == Evil_Eyes.CLAIRVOYANCE_ITEM) {
            return "千里眼";
        }
        if (item == mirror_of_then_and_now.MIRROR_ITEM) {
            return "镜子";
        }
        if (item == ThroughItem.THROUGH_ITEM) {
            return "穿墙";
        }
        if (item == ImitateItem.IMITATE_ITEM) {
            return "模仿";
        }
        if (item == PlantMagicItems.PLANT_WAND) {
            return "植物";
        }
        if (item == GravityItems.GRAVITY_WAND) {
            return "重力";
        }
        return null;
    }

    /**
     * 注册所有 S2C 网络包的客户端处理回调。
     * 每个回调在客户端主线程执行，对接收到的数据进行解析和状态更新。
     */
    public static void register() {
        // 1. 千里眼全局配置接收
        ClientPlayNetworking.registerGlobalReceiver(GlobalConfigS2C.ID, (packet, context) -> {
            context.client().execute(() -> {
                try {
                    JsonObject root = JsonParser.parseString(packet.json()).getAsJsonObject();
                    GlobalConfigS2C.StageConfig[] configs = new GlobalConfigS2C.StageConfig[7];
                    for (int i = 1; i <= 7; i++) {
                        JsonObject stageObj = root.getAsJsonObject("stage" + i);
                        configs[i - 1] = new GlobalConfigS2C.StageConfig(
                                stageObj.get("dailyLimit").getAsInt(),
                                stageObj.get("maxMarks").getAsInt(),
                                stageObj.get("minScore").getAsInt(),
                                stageObj.get("maxScore").getAsInt(),
                                stageObj.get("watchRequiredTicks").getAsInt(),
                                stageObj.get("parrotDailyLimit").getAsInt(),
                                jsonInt(stageObj, "maxActiveParrots", 1),
                                jsonDouble(stageObj, "uiDrainRate", 1.0D),
                                jsonDouble(stageObj, "watchDrainRate", 8.0D),
                                jsonDouble(stageObj, "regenRate", 2.0D)
                        );
                    }
                    CombinedConfigScreen.receiveEvilConfigs(configs);
                    com.kuilunfuzhe.monvhua.features.floating.floating.syncStageRanges(configs);
                } catch (Exception e) { e.printStackTrace(); }
            });
        });

        // 2. 打开/关闭 UI（旧系统）
        ClientPlayNetworking.registerGlobalReceiver(ClairvoyanceEnergyS2C.ID, (packet, context) -> {
            context.client().execute(() -> ClairvoyanceEnergyClient.setEnergy(packet.currentEnergy(), packet.maxEnergy()));
        });

        ClientPlayNetworking.registerGlobalReceiver(OpenUIS2C.ID, (packet, context) -> {
            context.client().execute(() -> {
                if (context.client().currentScreen instanceof com.kuilunfuzhe.monvhua.gui.evil_eyes.Evil_eyesScreen) context.client().setScreen(null);
                else context.client().setScreen(new com.kuilunfuzhe.monvhua.gui.evil_eyes.Evil_eyesScreen());
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ViewModeS2C.ID, (packet, context) -> {
            context.client().execute(() -> Evil_EyesClient.setViewMode(packet.mode()));
        });

        // 3. 实体标记更新
        ClientPlayNetworking.registerGlobalReceiver(EntityMarkedS2C.ID, (packet, context) -> {
            context.client().execute(() -> {
                UUID uuid = packet.entityUuid();
                if (uuid.getMostSignificantBits() == 0 && uuid.getLeastSignificantBits() == 0) {
                    Evil_EyesClient.localMarkedEntities.clear();
                    Evil_EyesClient.localMarkedEntityNames.clear();
                    com.kuilunfuzhe.monvhua.gui.evil_eyes.Evil_eyesScreen.updateMarkedList(Evil_EyesClient.localMarkedEntities);
                    return;
                }
                long expire = context.client().world != null ? context.client().world.getTime() + 60 : System.currentTimeMillis() / 50 + 60;
                Evil_EyesClient.localMarkedEntities.put(uuid, expire);
                Evil_EyesClient.localMarkedEntityNames.put(uuid, new Evil_EyesClient.MarkedEntityName(packet.entityName(), packet.entityTag()));
                com.kuilunfuzhe.monvhua.gui.evil_eyes.Evil_eyesScreen.updateMarkedList(Evil_EyesClient.localMarkedEntities);
            });
        });

        // 4. 切换图片显示
        ClientPlayNetworking.registerGlobalReceiver(ToggleImagesS2CPacket.ID, (packet, context) -> {
            context.client().execute(() -> BackTextureRenderer.imagesEnabled = packet.enabled());
        });

        // 5. 选择观看
        ClientPlayNetworking.registerGlobalReceiver(SelectView.ID, (packet, context) -> {
            context.client().execute(() -> {
                CameraWatchClientHandler.onUnbind();
                Evil_EyesClient.onSelectView(packet.entityUuid());
            });
        });

        // 6. 强制退出观看（旧系统）
        ClientPlayNetworking.registerGlobalReceiver(ForceExitViewS2C.ID, (packet, context) -> {
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
                CombinedConfigScreen.receiveGazeConfig(config);
            });
        });

        // mirror config sync S2C
        ClientPlayNetworking.registerGlobalReceiver(ConfigS2C.ID, (packet, context) -> {
            context.client().execute(() -> {
                MirrorConfig config = MirrorConfig.fromJson(packet.json());
                CombinedConfigScreen.receiveMirrorConfig(config);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ThroughConfigS2CPacket.ID, (packet, context) -> {
            context.client().execute(() -> {
                ThroughConfig config = ThroughConfig.fromJson(packet.json());
                CombinedConfigScreen.receiveThroughConfig(config);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(FloatingPackets.ConfigS2C.ID, (packet, context) -> {
            context.client().execute(() -> {
                FloatingConfig config = FloatingConfig.fromJson(packet.json());
                FloatingConfig.syncInstance(config);
                CombinedConfigScreen.receiveFloatingConfig(config);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(PlantMagicPackets.ConfigS2C.ID, (packet, context) -> {
            context.client().execute(() -> {
                PlantMagicConfig config = PlantMagicConfig.fromJson(packet.json());
                PlantMagicConfig.syncInstance(config);
                CombinedConfigScreen.receivePlantMagicConfig(config);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ThroughStateS2CPacket.ID, (packet, context) -> {
            context.client().execute(() -> ThroughClientManager.setState(
                    packet.invisible(),
                    packet.phaseNoClip(),
                    packet.phaseLocked(),
                    packet.phaseStalled(),
                    packet.lockedYaw(),
                    packet.lockedPitch(),
                    packet.phaseSpeedMultiplier(),
                    packet.fadeOutTicks()
            ));
        });

        // 9. 能量同步
        ClientPlayNetworking.registerGlobalReceiver(EnergySyncPacket.ID, (packet, context) -> {
            context.client().execute(() -> GazeguidanceClient.setEnergy(packet.currentEnergy(), packet.maxEnergy()));
        });

        // 漂浮：同步服务端玩家标签状态
        ClientPlayNetworking.registerGlobalReceiver(FullWitchTagSyncS2CPacket.ID, (packet, context) -> {
            context.client().execute(() -> com.kuilunfuzhe.monvhua.features.floating.floating.syncFullWitchTag(
                    packet.hasFullWitchTag(),
                    packet.hasFullWitchFlight()
            ));
        });

        // 10. 标记数量同步
        ClientPlayNetworking.registerGlobalReceiver(MarkCountPacket.ID, (packet, context) -> {
            context.client().execute(() -> GazeguidanceClient.setMarkCount(packet.count()));
        });

        ClientPlayNetworking.registerGlobalReceiver(MarkedListPacket.ID, (packet, context) -> {
            context.client().execute(() -> GazeguidanceClient.setMarkedNames(packet.entries()));
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
        ClientPlayNetworking.registerGlobalReceiver(AnchorParticleS2C.ID, (packet, context) -> {
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
        ClientPlayNetworking.registerGlobalReceiver(PlayerStageS2C.ID, (packet, context) -> {
            context.client().execute(() -> {
                int newStage = packet.stage();
                MonvhuaModClient.currentPlayerStage = newStage;
                GravityClient.setCurrentStage(newStage);
                MinecraftClient client = context.client();
                if (client.player != null) {
                    String magicName = currentStageToastMagicName(client);
                    if (magicName != null) {
                        if (newStage != lastNotifiedStage) {
                            lastNotifiedStage = newStage;
                            showStageUpgradeToast(magicName, newStage);
                        }
                    }
                }
            });
        });

        // 15. 爆炸粒子接收
        ClientPlayNetworking.registerGlobalReceiver(ExplosionParticleS2C.ID, (packet, context) -> {
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
        ClientPlayNetworking.registerGlobalReceiver(StateS2C.ID, (packet, context) -> {
            context.client().execute(() -> MirrorClientManager.onStatePacket(packet));
        });

        // 镜像充能同步接收
        ClientPlayNetworking.registerGlobalReceiver(ChargeSyncS2C.ID, (packet, context) -> {
            context.client().execute(() -> MirrorClientManager.setCharge(packet.currentTicks(), packet.maxTicks()));
        });

        ClientPlayNetworking.registerGlobalReceiver(CarryPoseSyncS2CPacket.ID, (packet, context) -> {
            context.client().execute(() -> CarryPoseClientState.apply(packet));
        });

        ClientPlayNetworking.registerGlobalReceiver(HoldHandsSyncS2CPacket.ID, (packet, context) -> {
            context.client().execute(() -> HoldHandsClientState.apply(packet));
        });

        ClientPlayNetworking.registerGlobalReceiver(CarryTransformPackets.ConfigS2C.ID, (packet, context) -> {
            context.client().execute(() -> CarryPoseTuning.applyTransformConfig(CarryTransformConfig.fromJson(packet.json())));
        });

        ClientPlayNetworking.registerGlobalReceiver(ActionsConfigS2C.ID, (packet, context) -> {
            context.client().execute(() -> {
                ActionEditorFragment inst = ActionEditorFragment.activeInstance;
                if (inst != null) inst.receiveConfig(ActionConfig.fromJson(packet.json()));
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ActionFilesListS2C.ID, (packet, context) -> {
            context.client().execute(() -> {
                ActionEditorFragment inst = ActionEditorFragment.activeInstance;
                if (inst != null) inst.receiveFileList(packet.files());
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(PreviewResultS2C.ID, (packet, context) -> {
            context.client().execute(() -> {
                ActionEditorFragment inst = ActionEditorFragment.activeInstance;
                if (inst != null) inst.receivePreviewResult(packet.actionId(), packet.previewText());
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(PreviewTimelineResultS2C.ID, (packet, context) -> {
            context.client().execute(() -> {
                ActionEditorFragment inst = ActionEditorFragment.activeInstance;
                if (inst != null) inst.receiveTimelinePreviewResult(packet.entries());
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(TimelineStateS2C.ID, (packet, context) -> {
            context.client().execute(() -> {
                TimelineClientState.currentSecond = packet.currentSecond();
                TimelineClientState.running = packet.running();
                TimelineClientState.paused = packet.paused();
                TimelineClientState.loop = packet.loop();
                TimelineClientState.totalSeconds = packet.totalSeconds();
                ActionEditorFragment inst = ActionEditorFragment.activeInstance;
                if (inst != null) inst.syncTimelineClock();
                ActionEditorFragment.tickActive();
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ActionPoseS2C.ID, (packet, context) -> {
            context.client().execute(() ->
                    ActionPoseClientState.apply(packet.entityId(), packet.poseValues(), packet.durationTicks()));
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            CarryPoseClientState.clear();
            CarryPoseTuning.resetTransformConfig();
            ActionPoseClientState.clear();
            Evil_EyesClient.setViewMode("viewport");
            com.kuilunfuzhe.monvhua.features.evil_eyes.ClairvoyanceViewportRenderer.cleanup();
            com.kuilunfuzhe.monvhua.features.floating.floating.syncFullWitchTag(false, false);
            com.kuilunfuzhe.monvhua.features.floating.floating.resetStageRanges();
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

    private static int jsonInt(JsonObject object, String key, int fallback) {
        return object.has(key) ? object.get(key).getAsInt() : fallback;
    }

    private static double jsonDouble(JsonObject object, String key, double fallback) {
        return object.has(key) ? object.get(key).getAsDouble() : fallback;
    }
}
