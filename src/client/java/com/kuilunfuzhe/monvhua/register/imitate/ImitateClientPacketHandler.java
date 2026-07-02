package com.kuilunfuzhe.monvhua.register.imitate;

import com.kuilunfuzhe.monvhua.client.imitate.AreaSelectClientManager;
import com.kuilunfuzhe.monvhua.client.imitate.ImitateClientManager;
import com.kuilunfuzhe.monvhua.client.imitate.ImitateCooldownNotifier;
import com.kuilunfuzhe.monvhua.client.imitate.SoundWaveClientManager;
import com.kuilunfuzhe.monvhua.gui.CombinedConfigScreen;
import com.kuilunfuzhe.monvhua.gui.imitate.ImitateScreen;
import com.kuilunfuzhe.monvhua.gui.imitate.SilenceTargetScreen;
import com.kuilunfuzhe.monvhua.item.config.ImitateConfig;
import com.kuilunfuzhe.monvhua.network.imitate.ImitateConfigS2CPacket;
import com.kuilunfuzhe.monvhua.network.imitate.ImitateOpenUIPacket;
import com.kuilunfuzhe.monvhua.network.imitate.ImitateSyncS2CPacket;
import com.kuilunfuzhe.monvhua.network.imitate.AreaImitateSelectPacket;
import com.kuilunfuzhe.monvhua.network.imitate.ResetCooldownsS2CPacket;
import com.kuilunfuzhe.monvhua.network.imitate.SilencePacket;
import com.kuilunfuzhe.monvhua.network.imitate.SilenceTargetsS2CPacket;
import com.kuilunfuzhe.monvhua.network.imitate.SoundWaveStartS2CPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

public class ImitateClientPacketHandler {

    public static void register() {
        SoundWaveClientManager.initialize();
        ImitateClientManager.initialize();
        ImitateCooldownNotifier.initialize();
        AreaSelectClientManager.initialize();

        SilencePacket.register();
        SilenceTargetsS2CPacket.register();
        AreaImitateSelectPacket.register();

        ClientPlayNetworking.registerGlobalReceiver(ImitateOpenUIPacket.ID, (packet, context) -> {
            context.client().execute(() -> {
                MinecraftClient client = MinecraftClient.getInstance();
                client.setScreen(new ImitateScreen(packet.witchStage()));
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(SoundWaveStartS2CPacket.ID, (packet, context) -> {
            context.client().execute(() -> {
                Vec3d center = new Vec3d(packet.x(), packet.y(), packet.z());
                SoundWaveClientManager.startShockwave(center, packet.maxRadius());
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ImitateSyncS2CPacket.ID, (packet, context) -> {
            context.client().execute(() -> {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null) {
                    String roleName = packet.roleName().isEmpty() ? null : packet.roleName();
                    ImitateClientManager.setImitate(
                        client.player.getUuid(),
                        roleName,
                        packet.endTime(),
                        packet.switchCooldownEnd(),
                        packet.soundWaveCooldownEnd()
                    );
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(SilenceTargetsS2CPacket.ID, (packet, context) -> {
            context.client().execute(() -> {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.currentScreen instanceof SilenceTargetScreen screen) {
                    screen.receiveTargets(packet);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ImitateConfigS2CPacket.ID, (packet, context) -> {
            context.client().execute(() -> {
                MinecraftClient client = MinecraftClient.getInstance();
                ImitateConfig newConfig = packet.getConfig();
                ImitateConfig.setInstance(newConfig);
                
                CombinedConfigScreen.receiveImitateConfig(newConfig);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ResetCooldownsS2CPacket.ID, (packet, context) -> {
            context.client().execute(() -> {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null) {
                    ImitateClientManager.resetCooldowns(client.player.getUuid());
                }
            });
        });
    }
}
