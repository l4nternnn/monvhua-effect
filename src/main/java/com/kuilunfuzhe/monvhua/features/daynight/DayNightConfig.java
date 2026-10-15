package com.kuilunfuzhe.monvhua.features.daynight;

import java.util.*;

/** enabled=true: mod owns the sun; false: datapack owns all clock calculations. */
public record DayNightConfig(boolean enabled, List<DayNightSegment> segments) {
    public DayNightConfig { segments=segments==null?List.of():List.copyOf(segments); }
    public DayNightConfig(boolean enabled,int dawn,int day,int dusk,int night) {
        this(enabled,List.of(new DayNightSegment("dawn","日出",0,1000,dawn,null),
                new DayNightSegment("day","白天",1000,12000,day,null),
                new DayNightSegment("dusk","日落",12000,13000,dusk,null),
                new DayNightSegment("night","夜晚",13000,0,night,null)));
    }
    public static DayNightConfig defaults() { return new DayNightConfig(false,60,900,60,1140); }
    public boolean valid() { return validationError().isEmpty(); }
    public String validationError() {
        if(segments.size()<1||segments.size()>32) return "时段数量必须为 1～32";
        Set<String> ids=new HashSet<>();int span=0;
        for(int i=0;i<segments.size();i++) {
            DayNightSegment s=segments.get(i),next=segments.get((i+1)%segments.size());
            if(s.id()==null||s.id().isBlank()||s.id().length()>64||!ids.add(s.id())) return "时段 ID 无效或重复";
            if(s.name()==null||s.name().isBlank()||s.name().codePointCount(0,s.name().length())>32
                    ||s.name().codePoints().anyMatch(Character::isISOControl)) return "名称须为 1～32 字符，不可包含换行";
            if(s.start()<0||s.start()>=24000||s.end()<0||s.end()>=24000) return "游戏时间范围为 0～23999";
            if(s.seconds()<1||s.seconds()>86400) return "每段持续时间须为 1～86400 整数秒";
            if(s.color()!=null&&(s.color()<0||s.color()>0xFFFFFF)) return "颜色须为 #RRGGBB";
            if(s.end()!=next.start()) return segments.size()==1?"单段模式起点和终点须相同，表示完整一天":"相邻区间必须首尾相接";
            if(segments.size()>1&&s.start()==s.end()) return "时段区间不能为零";
            span+=s.span();
        }
        return span==24000?"":"区间必须恰好覆盖全天一次，不能重叠或留空";
    }
    public int seconds(int i) { return segments.get(i).seconds(); }
    public int totalSeconds() { int total=0; for(DayNightSegment s:segments) total+=s.seconds(); return total; }
    public long totalTicks() { return totalSeconds()*20L; }
    public DayNightConfig withEnabled(boolean value) { return new DayNightConfig(value,segments); }
    public DayNightConfig edit(int index,String name,int start,int end,int seconds,Integer color) {
        ArrayList<DayNightSegment> list=new ArrayList<>(segments);
        DayNightSegment old=list.get(index);
        if(list.size()>1) {
            int previous=(index+list.size()-1)%list.size(),next=(index+1)%list.size();
            list.set(previous,list.get(previous).endAt(start));
            list.set(next,list.get(next).startAt(end));
        }
        list.set(index,new DayNightSegment(old.id(),name,start,end,seconds,color));
        return checked(new DayNightConfig(enabled,list));
    }
    public DayNightConfig split(int index) {
        if(segments.size()>=32) throw new IllegalArgumentException("最多支持 32 段");
        DayNightSegment s=segments.get(index);
        if(s.span()<2||s.seconds()<2) throw new IllegalArgumentException("区间至少 2 游戏刻且持续至少 2 秒，才能拆分");
        int middle=Math.floorMod(s.start()+s.span()/2,24000),seconds=s.seconds()/2;
        ArrayList<DayNightSegment> list=new ArrayList<>(segments);
        list.set(index,new DayNightSegment(s.id(),s.name(),s.start(),middle,seconds,s.color()));
        list.add(index+1,new DayNightSegment(UUID.randomUUID().toString(),"新时段",middle,s.end(),s.seconds()-seconds,null));
        return checked(new DayNightConfig(enabled,list));
    }
    public DayNightConfig mergePrevious(int index) {
        if(segments.size()==1) throw new IllegalArgumentException("至少保留一个时段");
        int previous=(index+segments.size()-1)%segments.size();
        DayNightSegment a=segments.get(previous),b=segments.get(index);
        ArrayList<DayNightSegment> list=new ArrayList<>(segments);
        list.set(previous,new DayNightSegment(a.id(),a.name(),a.start(),b.end(),a.seconds()+b.seconds(),a.color()));
        list.remove(index); return checked(new DayNightConfig(enabled,list));
    }
    private static DayNightConfig checked(DayNightConfig config) {
        if(!config.valid()) throw new IllegalArgumentException(config.validationError());return config;
    }
    public static int parseTime(String text) {
        text=text.trim();
        if(text.matches("[0-9]{1,5}")) {
            int value=Integer.parseInt(text);
            if(value<=24000) return value%24000;
        } else if(text.matches("[0-9]{1,2}:[0-9]{2}")) {
            String[] parts=text.split(":");int h=Integer.parseInt(parts[0]),m=Integer.parseInt(parts[1]);
            if(h<24&&m<60) return Math.floorMod((int)Math.round((h*60+m)*1000.0/60)-6000,24000);
        }
        throw new IllegalArgumentException("时间请输入 0～24000 游戏刻，或 HH:mm（00:00～23:59）");
    }
    public static String timeText(int tick) {
        int minute=Math.floorMod((int)Math.round(Math.floorMod(tick+6000,24000)*60.0/1000),1440);
        return String.format(Locale.ROOT,"%02d:%02d",minute/60,minute%60);
    }
}
