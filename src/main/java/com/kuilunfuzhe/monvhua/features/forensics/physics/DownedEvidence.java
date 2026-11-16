package com.kuilunfuzhe.monvhua.features.forensics.physics;

import net.minecraft.nbt.NbtCompound;
import java.util.UUID;

/** Server-only downing context. Actual death keeps its own time/cause; rescue does not erase injuries. */
public final class DownedEvidence {
    private DownedEvidence() {}
    public static void begin(NbtCompound life,long tick) {
        var evidence=new NbtCompound();evidence.putString("session",UUID.randomUUID().toString());evidence.putLong("tick",tick);
        var hits=life.getListOrEmpty("hits");
        if(!hits.isEmpty()) {
            var hit=hits.getCompound(hits.size()-1).orElse(new NbtCompound());
            if(hit.getBoolean("potentially_lethal",false)&&hit.getLong("tick",-1)==tick)evidence.put("hit",hit.copy());
        }
        life.put(KomDyingCompat.EVIDENCE,evidence);
    }
    public static boolean synthetic(NbtCompound life,String damageType) {
        return damageType.equals("minecraft:generic_kill")&&life.contains(KomDyingCompat.EVIDENCE);
    }
    public static void rescued(NbtCompound life){life.remove(KomDyingCompat.EVIDENCE);}
}
