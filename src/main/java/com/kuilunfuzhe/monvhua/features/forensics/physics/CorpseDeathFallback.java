package com.kuilunfuzhe.monvhua.features.forensics.physics;

import com.kuilunfuzhe.monvhua.features.block.body.BodyPartManager;
import com.kuilunfuzhe.monvhua.features.forensics.*;
import net.minecraft.server.network.ServerPlayerEntity;

/** Authority-thread terminal transitions that can bypass Fabric AFTER_DEATH entirely. */
public final class CorpseDeathFallback {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger("monvhua-death-fallback");
    private CorpseDeathFallback() {}
    public static void departure(ServerPlayerEntity player,String reason){
        // Disconnect, kick, spectator and shutdown are never death notifications.
        if(!reason.equals("death"))return;
        var server=player.getServer();
        if(server==null||!server.isOnThread()||server.isStopping()||!Forensics.enabled(player))return;
        var store=ForensicStore.get(server);var life=store.life(player.getUuid());
        boolean terminal=false;
        if(!TerminalDeathRules.shouldFinalize(life,player.isAlive(),terminal,false))return;
        // Store the exceptional transition explicitly; do not fake damage, health or a rescue.
        life.putString("terminal_transition",reason);life.putBoolean("terminal_without_death_event",true);store.markDirty();
        var source=player.getRecentDamageSource();if(source==null)source=player.getDamageSources().genericKill();
        try {
            BodyPartManager.spawnDeath(player,source);
            if(life.getBoolean("spawn_started",false)){
                KomDyingCompat.finishTerminal(player);
                LOG.info("Finalized exceptional death victim={} reason={} corpse={}",player.getUuid(),reason,life.getString("corpse",""));
            }
        } catch(RuntimeException e){LOG.error("Could not finalize exceptional death victim={} reason={}",player.getUuid(),reason,e);}
    }
}
