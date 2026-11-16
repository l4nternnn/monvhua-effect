package com.kuilunfuzhe.monvhua.features.forensics.physics;

import net.minecraft.nbt.NbtCompound;

/** A mode change or disconnect alone is not evidence of death. */
public final class TerminalDeathRules {
    private TerminalDeathRules() {}
    public static boolean shouldFinalize(NbtCompound life,boolean alive,boolean terminalExit,boolean stopping){
        if(stopping||life.getBoolean("spawn_started",false)||life.contains("corpse"))return false;
        return !alive||terminalExit&&life.contains(KomDyingCompat.EVIDENCE);
    }
}
