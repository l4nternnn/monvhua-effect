package com.kuilunfuzhe.monvhua.features.daynight;

import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;

/** Versioned handoff API. Business clock units/conversions belong exclusively to the pack. */
public final class DayNightLegacyBridge {
    public static final String OBJECTIVE = "monvhua.clock";
    public static final int API = 2;
    private static final String[] FUNCTIONS = {"probe", "enter_mod", "enter_pack", "before_mod", "mod_tick"};
    public static boolean available(MinecraftServer server) { return diagnose(server).isEmpty(); }
    public static String diagnose(MinecraftServer server) {
        for (String name : FUNCTIONS) {
            if (server.getCommandFunctionManager().getFunction(Identifier.of("times", "bridge/" + name)).isEmpty())
                return "当前世界缺少适配函数 times:bridge/"+name+"；请安装适配包并 /reload";
        }
        if(server.getScoreboard().getNullableObjective(OBJECTIVE)==null)
            return "适配函数已安装，但初始化记分板缺失；请 /reload 并检查加载日志";
        int api=score(server,"#api",OBJECTIVE,0);
        if(api!=API) return "数据包协议版本为 "+api+"，当前模组需要 "+API;
        write(server,"#ack",0);call(server,"probe");
        if(score(server,"#ack",OBJECTIVE,0)!=API) {
            int reason=score(server,"#reason",OBJECTIVE,0);
            return switch(reason) {
                case 10 -> "数据包参数 Count5 TimeRun 未初始化或无效";
                case 11 -> "数据包参数 Count9 TimeRun 未初始化或无效";
                default -> "数据包探测未确认，请检查 times:bridge/probe 与加载日志";
            };
        }
        return "";
    }
    public static boolean enter(MinecraftServer server, boolean mod) {
        write(server,"#ack",0);
        call(server, mod ? "enter_mod" : "enter_pack");
        return score(server,"#ack",OBJECTIVE,0) == API
                && score(server,"#owner",OBJECTIVE,-1) == (mod ? 1 : 0);
    }
    public static void call(MinecraftServer server, String name) {
        server.getCommandManager().executeWithPrefix(server.getCommandSource().withSilent().withLevel(2),
                "function times:bridge/" + name);
    }
    public static int score(MinecraftServer server, String holder, String objective, int fallback) {
        var board=server.getScoreboard(); var obj=board.getNullableObjective(objective);
        if (obj == null) return fallback;
        var value=board.getScore(ScoreHolder.fromName(holder),obj);
        return value == null ? fallback : value.getScore();
    }
    public static void write(MinecraftServer server, String holder, int value) {
        var board=server.getScoreboard(); var obj=board.getNullableObjective(OBJECTIVE);
        if (obj != null) board.getOrCreateScore(ScoreHolder.fromName(holder),obj).setScore(value);
    }
    public static boolean paused(MinecraftServer server) {
        return score(server,"GlobalTime","TimeState",0) == 1;
    }
}
