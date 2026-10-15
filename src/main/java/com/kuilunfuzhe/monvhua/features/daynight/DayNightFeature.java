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
    private static final Map<MinecraftServer, Runtime> RUNTIME = new WeakHashMap<>();
    private static final class Runtime {
        boolean ready, reconcile=true, slept;
        int kind, status=-1;
        long before, produced;
        String error="";
    }
    public static void initialize() {
        DayNightPackets.register();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            Runtime rt=new Runtime(); RUNTIME.put(server,rt);
            try { STATES.put(server,DayNightState.load(server)); }
            catch (IOException e) {
                rt.error="配置读取失败，原文件未覆盖";
                MonvhuaMod.LOGGER.error("Cannot load day/night configuration",e);
            }
        });
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
            Runtime rt=RUNTIME.get(server);
            if (rt != null && success) { rt.reconcile=true; rt.ready=false; }
        });
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server) -> reply(handler.getPlayer(),"",0));
        ServerTickEvents.START_SERVER_TICK.register(DayNightFeature::start);
        ServerTickEvents.END_SERVER_TICK.register(DayNightFeature::end);
        ServerLifecycleEvents.SERVER_STOPPING.register(DayNightFeature::save);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { STATES.remove(server); RUNTIME.remove(server); });
        ServerPlayNetworking.registerGlobalReceiver(DayNightPackets.Request.ID,(packet,context) ->
                context.server().execute(() -> handle(context.server(),context.player(),packet)));
    }
    private static boolean daylight(MinecraftServer server) {
        return server.getOverworld().getGameRules().getBoolean(GameRules.DO_DAYLIGHT_CYCLE);
    }
    private static void start(MinecraftServer server) {
        DayNightState state=STATES.get(server); Runtime rt=RUNTIME.get(server);
        if (state==null || rt==null) return;
        if (rt.reconcile || (!rt.ready && server.getTicks()%40==0)) {
            rt.reconcile=false;
            rt.error=DayNightLegacyBridge.diagnose(server);
            rt.ready=rt.error.isEmpty();
            if (rt.ready) {
                boolean same=DayNightLegacyBridge.score(server,"#owner",DayNightLegacyBridge.OBJECTIVE,-1)==(state.config.enabled()?1:0);
                boolean wasDaylight=daylight(server);
                rt.ready=DayNightLegacyBridge.enter(server,state.config.enabled());
                if (same && state.config.enabled() && !wasDaylight)
                    server.getOverworld().getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false,server);
            }
            if(rt.ready) rt.error="";
            else if(rt.error.isEmpty()) rt.error="数据包未确认控制权交接，请检查适配函数日志";
        }
        rt.kind=0; rt.slept=false;
        if (rt.ready && state.config.enabled()) DayNightLegacyBridge.call(server,"before_mod");
        rt.before=server.getOverworld().getTimeOfDay(); rt.produced=rt.before;
    }
    public static long nextTime(ServerWorld world,long vanilla) {
        DayNightState state=STATES.get(world.getServer()); Runtime rt=RUNTIME.get(world.getServer());
        if (!world.getRegistryKey().equals(World.OVERWORLD) || state==null || !state.config.enabled()) return vanilla;
        long now=world.getTimeOfDay();
        if (rt==null || !rt.ready || DayNightLegacyBridge.paused(world.getServer())) return now;
        boolean seek=state.clock.expected!=Long.MIN_VALUE && state.clock.expected!=now;
        long next=state.clock.advance(now,state.config);
        rt.produced=next;
        rt.kind=rt.slept?4:seek?3:next==now?0:Math.floorDiv(next,24000L)>Math.floorDiv(now,24000L)?2:1;
        return next;
    }
    public static long sleepTime(ServerWorld world,long proposed) {
        DayNightState state=STATES.get(world.getServer()); Runtime rt=RUNTIME.get(world.getServer());
        if (world.getRegistryKey().equals(World.OVERWORLD) && state!=null && state.config.enabled()) {
            if (rt==null || !rt.ready || DayNightLegacyBridge.paused(world.getServer())) return world.getTimeOfDay();
            rt.slept=true;
        }
        return proposed;
    }
    private static void end(MinecraftServer server) {
        DayNightState state=STATES.get(server); Runtime rt=RUNTIME.get(server);
        if (state==null || rt==null) return;
        if (rt.ready && state.config.enabled()) {
            // Tick-tag functions can write the legacy score AFTER START_SERVER_TICK.
            // Consume them before publishing the new sun, otherwise skip-night requests get lost.
            DayNightLegacyBridge.call(server,"before_mod");
            long now=server.getOverworld().getTimeOfDay();
            if (now!=rt.produced) rt.kind=rt.slept?4:3;
            DayNightLegacyBridge.write(server,"#running",daylight(server)&&!DayNightLegacyBridge.paused(server)?1:0);
            DayNightLegacyBridge.write(server,"#kind",rt.kind);
            DayNightLegacyBridge.call(server,"mod_tick");
        }
        int status=(rt.ready?1:0)|(daylight(server)?2:0)|(DayNightLegacyBridge.paused(server)?4:0);
        if (status!=rt.status) { rt.status=status; broadcast(server,null,"",0); }
        if (server.getTicks()%1200==0) save(server);
    }
    private static void handle(MinecraftServer server,ServerPlayerEntity player,DayNightPackets.Request request) {
        DayNightState state=STATES.get(server); Runtime rt=RUNTIME.get(server);
        if (state==null || rt==null) { reply(player,"世界配置不可用，请检查日志",2); return; }
        if (request.action()==0) { reply(player,"已读取服务器配置",0); return; }
        if (request.action()==4) {
            rt.error=DayNightLegacyBridge.diagnose(server);
            if(!rt.error.isEmpty()) rt.ready=false;
            else if(!rt.ready) rt.reconcile=true;
            reply(player,rt.error.isEmpty()?"适配检测通过":"适配检测失败，详见诊断",3);return;
        }
        if (!player.hasPermissionLevel(2)) { reply(player,"需要管理员权限",2); return; }
        if (request.revision()!=state.revision) { reply(player,"配置已被其他管理员修改，请重新读取后提交",2); return; }
        if (request.action()==2) {
            if (!rt.ready || !state.config.enabled()) { reply(player,"仅模组模式可在此恢复昼夜规则",2); return; }
            server.getOverworld().getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(true,server);
            reply(player,"已恢复昼夜循环规则",3); return;
        }
        if ((request.action()!=1&&request.action()!=3) || !request.config().valid()) {
            reply(player,request.config().validationError().isEmpty()?"未知操作":request.config().validationError(),2);return;
        }
        if(request.action()==3) {
            DayNightState draft=state.copy();draft.savedConfig=request.config();draft.revision++;
            try { draft.save(server);STATES.put(server,draft);broadcast(server,player,"配置已保存，当前运行模式和速度未改变",4); }
            catch(IOException e) { MonvhuaMod.LOGGER.error("Cannot save day/night draft",e);reply(player,"保存失败，输入已保留",2); }
            return;
        }
        String problem=DayNightLegacyBridge.diagnose(server);
        if (!problem.isEmpty()) { rt.error=problem;reply(player,"未应用："+problem,2);return; }
        boolean switching=state.config.enabled()!=request.config().enabled() || !rt.ready;
        DayNightState next=new DayNightState(); next.config=request.config(); next.savedConfig=request.config(); next.revision=state.revision+1;
        next.clock.anchor(server.getOverworld().getTimeOfDay(),next.config);
        // Persist before taking control; all operations run on the same server thread.
        try { next.save(server); }
        catch (IOException e) { MonvhuaMod.LOGGER.error("Cannot save day/night config",e); reply(player,"保存失败，原配置继续生效",2); return; }
        boolean wasDaylight=daylight(server);
        if (switching && !DayNightLegacyBridge.enter(server,next.config.enabled())) {
            boolean restored=DayNightLegacyBridge.enter(server,state.config.enabled());
            server.getOverworld().getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(wasDaylight,server);
            rt.ready=restored;
            try { state.save(server); } catch (IOException e) { MonvhuaMod.LOGGER.error("Cannot restore day/night config",e); }
            reply(player,"数据包未确认交接，已尝试恢复原模式",2); return;
        }
        STATES.put(server,next); rt.ready=true; rt.error="";
        broadcast(server,player,"已保存并应用",1);
    }
    private static void broadcast(MinecraftServer server,ServerPlayerEntity submitter,String message,int result) {
        for (ServerPlayerEntity player:server.getPlayerManager().getPlayerList())
            reply(player,player==submitter?message:"",player==submitter?result:0);
    }
    private static void reply(ServerPlayerEntity player,String message,int result) {
        MinecraftServer server=player.getServer(); Runtime rt=RUNTIME.get(server);
        DayNightState state=STATES.get(server); boolean available=state!=null;
        if (state==null) state=new DayNightState();
        if (ServerPlayNetworking.canSend(player,DayNightPackets.State.ID))
            ServerPlayNetworking.send(player,new DayNightPackets.State(state.revision,state.config,state.savedConfig,
                    available && player.hasPermissionLevel(2),daylight(server),rt!=null&&rt.ready,
                    DayNightLegacyBridge.paused(server),result,message,rt==null?"世界尚未就绪":rt.error));
    }
    private static void save(MinecraftServer server) {
        DayNightState state=STATES.get(server);
        if (state!=null) try { state.save(server); }
        catch (IOException e) { MonvhuaMod.LOGGER.error("Cannot save day/night state",e); }
    }
}
