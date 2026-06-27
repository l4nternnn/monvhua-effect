package com.kuilunfuzhe.monvhua.fantasy;

import com.kuilunfuzhe.monvhua.network.fantasy.FantasyS2CPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class FantasyClientHandler {

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(FantasyS2CPacket.ID, (packet, context) -> {
            context.client().execute(() -> {
                // 调用渲染器显示字幕
                FantasyRenderer.showText(
                        packet.text(),
                        packet.durationTicks(),
                        packet.isBurst(),
                        packet.isGlitch(),
                        packet.colorCode()
                );
            });
        });
    }
}