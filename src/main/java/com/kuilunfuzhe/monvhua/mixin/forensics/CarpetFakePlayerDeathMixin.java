package com.kuilunfuzhe.monvhua.mixin.forensics;

import com.kuilunfuzhe.monvhua.features.block.body.BodyPartManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Carpet's /kill overload removes a healthy bot without calling onDeath. */
@Pseudo
@Mixin(targets = "carpet.patches.EntityPlayerMPFake", remap = false)
public abstract class CarpetFakePlayerDeathMixin {
    @Unique private boolean monvhua$commandDeathHandled;

    // Exact descriptors exclude Carpet's kill(Text), used for ordinary bot removal.
    // Both namespaces are explicit because Carpet is an optional, unlinked target.
    @Inject(method = {
            "kill(Lnet/minecraft/server/world/ServerWorld;)V",
            "method_5768(Lnet/minecraft/class_3218;)V"
    }, at = @At("HEAD"), require = 0, remap = false)
    private void monvhua$commandDeath(ServerWorld world, CallbackInfo ci) {
        var player = (ServerPlayerEntity) (Object) this;
        var server = player.getServer();
        if (monvhua$commandDeathHandled || server == null || server != world.getServer()
                || !server.isOnThread() || server.isStopping()) return;
        if (!player.getCommandTags().contains("dead_body") && !player.getCommandTags().contains("dead_part")) return;
        // Also deduplicate when forensics is disabled and its persistent life guard is inactive.
        monvhua$commandDeathHandled = true;
        BodyPartManager.spawnDeath(player, player.getDamageSources().genericKill());
    }
}
