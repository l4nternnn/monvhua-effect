package com.kuilunfuzhe.monvhua.features.forensics;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.Locale;

/** Read-only snapshot of times:display's clock. Never reconstruct history from current world ticks. */
public final class EvidenceClock {
    private EvidenceClock() {}
    public static boolean master(ServerPlayerEntity player){return player.getCommandTags().contains("master");}
    public static String capture(ServerPlayerEntity player){
        var board=player.getScoreboard();var time=board.getNullableObjective("TimeRun");var days=board.getNullableObjective("Days");
        if(time==null||days==null)return "";
        var holder=ScoreHolder.fromName("GlobalTime");var t=board.getScore(holder,time);var d=board.getScore(holder,days);
        return t==null||d==null?"":format(t.getScore(),d.getScore());
    }
    public static String format(int time,int day){
        if(time<0||time>=43200||day<0)return "";
        int minute=(time/30+360)%1440;
        return String.format(Locale.ROOT,"第%d天 %02d:%02d",day,minute/60,minute%60);
    }
    public static void stamp(NbtCompound record,ServerPlayerEntity player){
        String time=capture(player);if(!time.isEmpty())record.putString("event_time",time);
    }
    public static String describe(String event,String death){
        return (event.isEmpty()?"":"我确认发生时间："+event)+(death.isEmpty()?"":(event.isEmpty()?"":"\n")+"我确认死亡时间："+death);
    }
}
