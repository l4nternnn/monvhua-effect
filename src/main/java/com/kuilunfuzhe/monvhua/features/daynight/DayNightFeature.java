package com.kuilunfuzhe.monvhua.features.daynight;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import java.io.IOException;
import java.util.Map;
import java.util.WeakHashMap;

public final class DayNightFeature {
    private static final Map<MinecraftServer, DayNightState> STATES = new WeakHashMap<>();
    private static final Map<MinecraftServer, String> ERRORS = new WeakHashMap<>();

    public static void initialize() {
        DayNightPackets.register();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try { STATES.put(server, DayNightState.load(server)); }
            catch (IOException e) {
                ERRORS.put(server, "昼夜配置读取失败，请检查服务器日志；未覆盖原文件");
                MonvhuaMod.LOGGER.error("Cannot load day/night configuration", e);
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            DayNightState state = STATES.get(server);
            if (state != null) reply(handler.getPlayer(), state, true, "");
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 1200 == 0) save(server);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(DayNightFeature::save);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { STATES.remove(server); ERRORS.remove(server); });
        ServerPlayNetworking.registerGlobalReceiver(DayNightPackets.Request.ID, (packet, context) ->
                context.server().execute(() -> handle(context.server(), context.player(), packet)));
    }

    public static long nextTime(ServerWorld world, long vanilla) {
        DayNightState state = STATES.get(world.getServer());
        if (!world.getRegistryKey().equals(World.OVERWORLD) || state == null || !state.config.enabled()) return vanilla;
        return state.clock.advance(world.getTimeOfDay(), state.config);
    }

    private static void handle(MinecraftServer server, ServerPlayerEntity player, DayNightPackets.Request request) {
        DayNightState state = STATES.get(server);
        if (state == null) { reply(player, new DayNightState(), false, ERRORS.getOrDefault(server, "世界尚未就绪")); return; }
        if (request.action() == 0) { reply(player, state, true, "已读取服务器配置"); return; }
        if (!player.hasPermissionLevel(2)) { reply(player, state, true, "需要管理员权限"); return; }
        if (request.revision() != state.revision) { reply(player, state, true, "配置已被修改，已重新加载，请重新编辑"); return; }
        if (request.action() == 2) {
            server.getOverworld().getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(true, server);
            reply(player, state, true, "已恢复昼夜循环游戏规则"); return;
        }
        if (request.action() != 1 || !request.config().valid()) { reply(player, state, true, "每段必须是 1～86400 的整数秒"); return; }
        DayNightState next = new DayNightState();
        next.config = request.config();
        next.revision = state.revision + 1;
        next.clock.anchor(server.getOverworld().getTimeOfDay(), next.config);
        try {
            next.save(server);
            STATES.put(server, next);
            for (ServerPlayerEntity other : server.getPlayerManager().getPlayerList()) {
                reply(other, next, true, other == player ? "已保存并应用" : "");
            }
        } catch (IOException e) {
            MonvhuaMod.LOGGER.error("Cannot save day/night configuration", e);
            reply(player, state, true, "保存失败，原配置继续生效");
        }
    }

    private static void reply(ServerPlayerEntity player, DayNightState state, boolean available, String message) {
        if (ServerPlayNetworking.canSend(player, DayNightPackets.State.ID))
            ServerPlayNetworking.send(player, new DayNightPackets.State(state.revision, state.config,
                    available && player.hasPermissionLevel(2), player.getServer().getOverworld().getGameRules()
                    .getBoolean(GameRules.DO_DAYLIGHT_CYCLE), message));
    }
    private static void save(MinecraftServer server) {
        DayNightState state = STATES.get(server);
        if (state != null) try { state.save(server); }
        catch (IOException e) { MonvhuaMod.LOGGER.error("Cannot save day/night state", e); }
    }
}
