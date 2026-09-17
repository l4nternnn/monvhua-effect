package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.swing.SwingEntity;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Validates a long swing against the actual seat hit instead of only its pivot entity. */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class SwingInteractionNetworkMixin {
    @Unique private SwingEntity monvhua$interactedSwing;

    @Redirect(
            method = "onPlayerInteractEntity",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getBoundingBox()Lnet/minecraft/util/math/Box;")
    )
    private Box monvhua$captureInteractedSwing(Entity entity) {
        monvhua$interactedSwing = entity instanceof SwingEntity swing ? swing : null;
        return entity.getBoundingBox();
    }

    @Redirect(
            method = "onPlayerInteractEntity",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayerEntity;canInteractWithEntityIn(Lnet/minecraft/util/math/Box;D)Z")
    )
    private boolean monvhua$allowLongSwingSeatInteraction(ServerPlayerEntity player, Box bounds,
                                                           double extraRange) {
        if (player.canInteractWithEntityIn(bounds, extraRange)) return true;
        return monvhua$interactedSwing != null && monvhua$interactedSwing.canPlayerReachSeat(player);
    }
}
