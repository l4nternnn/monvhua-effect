package com.kuilunfuzhe.monvhua.features.daynight;

/** Solar endpoints are 0..23999. Equal endpoints mean a full day only for a single segment. */
public record DayNightSegment(String id, String name, int start, int end, int seconds, Integer color) {
    public int span() { int span=Math.floorMod(end-start,24000); return span==0?24000:span; }
    public DayNightSegment startAt(int value) { return new DayNightSegment(id,name,value,end,seconds,color); }
    public DayNightSegment endAt(int value) { return new DayNightSegment(id,name,start,value,seconds,color); }
    public int displayColor() {
        if (color!=null) return 0xFF000000|color;
        int x=Math.floorMod(start+span()/2,24000);
        int[] anchors={500,6500,12500,18500,24500};
        int[] colors={0xF3BB83,0xE8D182,0xE59CAA,0x9A9BDA,0xF3BB83};
        if (x<500) x+=24000;
        int i=0;while(i<3&&x>anchors[i+1]) i++;
        double t=(x-anchors[i])/(double)(anchors[i+1]-anchors[i]);
        int result=0xFF000000;
        for(int shift:new int[]{16,8,0}) {
            int a=(colors[i]>>shift)&255,b=(colors[i+1]>>shift)&255;
            result|=((int)Math.round(a+(b-a)*t))<<shift;
        }
        return result;
    }
}
