package com.kuilunfuzhe.monvhua.features.forensics.cards;

import com.google.gson.*;
import com.kuilunfuzhe.monvhua.features.area_tip.AreaTipAreaStore;
import com.kuilunfuzhe.monvhua.item.config.AreaTipConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import java.nio.file.*;
import java.util.Comparator;

/** Pack-owned clock, read-only. Empty objective deliberately means not configured. */
public final class ClueCardConfig {
    public String timeObjective="";
    public String timeHolder="";
    public int unitsPerMinute=1;
    public int approximateMinutes=10;
    private static ClueCardConfig current=new ClueCardConfig();
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private ClueCardConfig(){}
    public static void load(){
        Path path=FabricLoader.getInstance().getConfigDir().resolve("monvhua_clue_cards.json");
        try{
            if(Files.exists(path)){try(var reader=Files.newBufferedReader(path)){var value=JSON.fromJson(reader,ClueCardConfig.class);if(value==null)throw new IllegalArgumentException("Empty clue card config");current=value;}}
            else Files.writeString(path,JSON.toJson(current));
        }catch(java.io.IOException|RuntimeException e){com.kuilunfuzhe.monvhua.MonvhuaMod.LOGGER.warn("Cannot load clue card clock config; time prefill disabled",e);current=new ClueCardConfig();}
    }
    public static String time(ServerPlayerEntity p){
        if(current.timeObjective==null||current.timeObjective.isBlank())return "";
        var scoreboard=p.getScoreboard();var objective=scoreboard.getNullableObjective(current.timeObjective);if(objective==null)return "";
        var holder=current.timeHolder==null||current.timeHolder.isBlank()?p:ScoreHolder.fromName(current.timeHolder);
        var score=scoreboard.getScore(holder,objective);if(score==null||score.getScore()<0)return "";
        long minutes=score.getScore()/Math.max(1,current.unitsPerMinute);int step=Math.max(1,Math.min(120,current.approximateMinutes));
        long rounded=minutes/step*step;return "约 "+String.format(java.util.Locale.ROOT,"%02d:%02d",rounded/60%24,rounded%60)+(rounded>=1440?"（第"+(rounded/1440+1)+"天）":"");
    }
    public static String place(ServerPlayerEntity p,Vec3d point){
        return AreaTipAreaStore.get(((net.minecraft.server.world.ServerWorld)p.getWorld())).toAreas().stream().filter(a->a.contains(point))
            .sorted(Comparator.comparingLong((AreaTipAreaStore.StoredArea a)->a.hasBlockSet()?a.blocks().size():((long)a.maxBlock().getX()-a.minBlock().getX()+1)*((long)a.maxBlock().getY()-a.minBlock().getY()+1)*((long)a.maxBlock().getZ()-a.minBlock().getZ()+1)).thenComparing(a->a.id().toString()))
            .map(a->AreaTipConfig.getInstance().findGroup(a.groupId()).map(g->g.name).orElse(""))
            .filter(name->name!=null&&!name.isBlank()).findFirst().orElse("");
    }
}
