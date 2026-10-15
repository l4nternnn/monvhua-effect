package com.kuilunfuzhe.monvhua.features.daynight;

import com.google.gson.*;
import java.util.*;

/** Runs the production clock/config code without loading a save or starting Minecraft. */
public final class DayNightControllerTest {
    private static void eq(long expected,long actual) {
        if(expected!=actual) throw new AssertionError("expected "+expected+", got "+actual);
    }
    private static void ok(boolean value,String message) { if(!value) throw new AssertionError(message); }
    private static void rejects(Runnable operation) {
        try {operation.run();throw new AssertionError("Expected invalid edit to be rejected");}
        catch(IllegalArgumentException expected) {}
    }
    private static long advance(DayNightController clock,DayNightConfig config,long time,long ticks) {
        for(long i=0;i<ticks;i++) {
            long next=clock.advance(time,config);if(next<time) throw new AssertionError("Time moved backwards");time=next;
        }
        return time;
    }
    private static DayNightConfig varied(int count,int origin) {
        ArrayList<DayNightSegment> list=new ArrayList<>();
        for(int i=0;i<count;i++) {
            int a=Math.floorMod(origin+(24000*i/count),24000),b=Math.floorMod(origin+(24000*(i+1)/count),24000);
            list.add(new DayNightSegment("s"+i,"时段 "+i,a,b,1+i%5,null));
        }
        return new DayNightConfig(true,list);
    }
    public static void main(String[] args) {
        DayNightConfig defaults=DayNightConfig.defaults();DayNightController clock=new DayNightController();long time=0;
        for(DayNightSegment segment:defaults.segments()) {
            time=advance(clock,defaults,time,segment.seconds()*20L);
            eq(segment.end()==0?24000:segment.end(),time);
        }
        eq(24000L*101,advance(clock,defaults,time,defaults.totalTicks()*100));
        ArrayList<DayNightConfig> configs=new ArrayList<>(List.of(defaults,new DayNightConfig(true,30,12,60,540),
                new DayNightConfig(true,1,1,1,1)));
        for(int count:new int[]{1,2,4,5,7,31,32}) {
            configs.add(varied(count,0));configs.add(varied(count,5713));
        }
        // Irregular, coprime boundaries and a tiny 1-tick solar segment.
        configs.add(new DayNightConfig(true,List.of(new DayNightSegment("a","A",0,1,1,null),
                new DayNightSegment("b","B",1,7919,7,null),new DayNightSegment("c","C",7919,17011,3,null),
                new DayNightSegment("d","D",17011,0,11,null))));
        for(DayNightConfig config:configs) {
            ok(config.valid(),config.validationError());
            for(long start:new long[]{-24001,-1,0,1,999,1000,5713,11999,13000,23999,9000001}) {
                clock=new DayNightController();
                eq(start+24000,advance(clock,config,start,config.totalTicks()));
                eq(start+48000,advance(clock,config,start+24000,config.totalTicks()));
            }
        }
        DayNightConfig max=new DayNightConfig(true,86400,86400,86400,86400);
        eq(1000,advance(new DayNightController(),max,0,86400*20L));
        clock=new DayNightController();advance(clock,defaults,0,123);
        eq(new DayNightController().advance(18000,defaults),clock.advance(18000,defaults));
        eq(new DayNightController().advance(500,defaults),clock.advance(500,defaults));
        DayNightConfig irregular=configs.getLast();clock=new DayNightController();time=advance(clock,irregular,1234,17);
        Gson gson=new Gson();DayNightController restored=gson.fromJson(gson.toJson(clock),DayNightController.class);
        eq(advance(clock,irregular,time,irregular.totalTicks()),advance(restored,irregular,time,irregular.totalTicks()));
        // Interval edits link both neighbours, including the cyclic first/last boundary.
        DayNightConfig edited=defaults.edit(0,"清晨",23000,2000,30,0x123456);
        eq(23000,edited.segments().getLast().end());eq(2000,edited.segments().get(1).start());
        ok(edited.valid(),edited.validationError());eq(0xFF123456L,Integer.toUnsignedLong(edited.segments().getFirst().displayColor()));
        eq(24000,advance(new DayNightController(),edited,0,edited.totalTicks()));
        DayNightConfig split=edited.split(0);eq(5,split.segments().size());eq(edited.totalSeconds(),split.totalSeconds());
        DayNightConfig merged=split.mergePrevious(1);eq(edited.totalSeconds(),merged.totalSeconds());
        eq(edited.segments().getFirst().span(),merged.segments().getFirst().span());
        DayNightConfig two=varied(2,5713).edit(0,"调整",6000,18000,4,null);
        eq(18000,two.segments().get(1).start());eq(6000,two.segments().get(1).end());
        DayNightConfig one=two.mergePrevious(0);eq(1,one.segments().size());eq(24000,one.segments().getFirst().span());
        rejects(() -> defaults.edit(1,"重叠",500,13000,30,null));
        rejects(() -> defaults.edit(0,"",0,1000,30,null));
        rejects(() -> defaults.edit(0,"颜色",0,1000,30,-1));
        rejects(() -> varied(32,0).split(0));
        rejects(() -> defaults.edit(0,"零区间",0,0,30,null));
        ok(!new DayNightConfig(true,0,1,1,1).valid(),"Zero duration accepted");
        eq(0,DayNightConfig.parseTime("06:00"));eq(18000,DayNightConfig.parseTime("00:00"));
        eq(0,DayNightConfig.parseTime("24000"));eq(6000,DayNightConfig.parseTime("12:00"));
        rejects(() -> DayNightConfig.parseTime("24:01"));
        // V1/V2 settings migrate without changing durations, ownership, names or solar intervals.
        DayNightConfig migrated=DayNightConfigMigration.read(JsonParser.parseString("{\"enabled\":true,\"dawn\":30,\"day\":12,\"dusk\":60,\"night\":540}"));
        eq(642,migrated.totalSeconds());ok(migrated.enabled(),"Migration lost mode");
        ok(migrated.equals(DayNightConfigMigration.read(JsonParser.parseString(gson.toJson(migrated)))),"V3 round trip failed");
        // Editing/saving a draft leaves the active immutable configuration untouched.
        DayNightConfig saved=migrated.split(1).withEnabled(false);
        ok(migrated.enabled()&&migrated.segments().size()==4&&saved.segments().size()==5,"Draft mutated active config");
        System.out.println("PASS: 1-32 segments, custom/rotated intervals, exact multi-day cycles, screenshot timings, boundary edits, split/merge, seek, persistence, migration and validation");
    }
}
