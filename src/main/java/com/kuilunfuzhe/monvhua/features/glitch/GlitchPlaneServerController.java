package com.kuilunfuzhe.monvhua.features.glitch;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

public final class GlitchPlaneServerController {
    private static boolean initialized;
    private GlitchPlaneServerController() {}
    public static void initialize() {
        if (initialized) return; initialized = true;
        CommandRegistrationCallback.EVENT.register(GlitchPlaneCommand::register);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> GlitchPlaneManager.syncTo(handler.getPlayer())));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> GlitchPlaneManager.syncTo(player));
    }
}
