package com.kuilunfuzhe.monvhua.features.textarea;

import com.kuilunfuzhe.monvhua.features.area_tip.AreaTipClient;
import com.kuilunfuzhe.monvhua.item.config.AreaTipConfig;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.math.BlockPos;

public final class TextAreaHudClient {
    private static AreaTipClient.AreaView activeArea;
    private static long activeSinceTick;
    private static long activeConfigRevision = -1L;
    private static Screen pendingEditorParent;

    private TextAreaHudClient() {
    }

    public static void initialize() {
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || TextGroupEditFragment.isActive()) {
                return;
            }
            AreaTipConfig.GroupConfig group = currentAreaGroup(client);
            if (group != null && group.hudVisible) {
                TextGroupRenderer.renderGroup(context, group, false, false, elapsedTicks(client));
            }
        });
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (pendingEditorParent != null && client.currentScreen == null) {
                Screen parent = pendingEditorParent;
                pendingEditorParent = null;
                TextGroupEditFragment.open(parent);
            }
        });
    }

    public static void openEditorAfterWorldFrame(Screen parent) {
        AreaTipClient.requestSync();
        pendingEditorParent = parent;
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> client.setScreen(null));
    }

    public static void resetPlayback() {
        MinecraftClient client = MinecraftClient.getInstance();
        activeSinceTick = client.world == null ? 0L : client.world.getTime();
        activeConfigRevision = AreaTipClient.configRevision();
    }

    private static AreaTipConfig.GroupConfig currentAreaGroup(MinecraftClient client) {
        BlockPos playerBlock = client.player.getBlockPos();
        for (AreaTipClient.AreaView area : AreaTipClient.areas()) {
            if (!area.containsBlock(playerBlock)) {
                continue;
            }
            long configRevision = AreaTipClient.configRevision();
            if (activeArea == null || !activeArea.id().equals(area.id()) || activeConfigRevision != configRevision) {
                activeArea = area;
                activeSinceTick = client.world == null ? 0L : client.world.getTime();
                activeConfigRevision = configRevision;
            }
            AreaTipConfig.GroupConfig group = AreaTipConfig.getInstance().findGroup(area.groupId()).orElse(null);
            if (group != null) {
                return group;
            }
        }
        activeArea = null;
        activeSinceTick = 0L;
        activeConfigRevision = AreaTipClient.configRevision();
        return null;
    }

    private static long elapsedTicks(MinecraftClient client) {
        if (client.world == null) {
            return 0L;
        }
        return Math.max(0L, client.world.getTime() - activeSinceTick);
    }
}
