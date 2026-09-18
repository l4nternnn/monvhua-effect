package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.swing.SwingEntity;
import com.kuilunfuzhe.monvhua.features.swing.SwingSpatialIndex;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Validates a long swing against the actual seat hit instead of only its pivot entity. */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class SwingInteractionNetworkMixin {
    @Unique private SwingEntity monvhua$interactedSwing;

    @Inject(method = "isEntityOnAir", at = @At("RETURN"), cancellable = true)
    private void monvhua$recognizeSwingSupport(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return;
        Box body = entity.getBoundingBox();
        Box feet = new Box(body.minX, body.minY - .55, body.minZ, body.maxX, body.minY + .0625, body.maxZ);
        for (SwingEntity swing : SwingSpatialIndex.find(entity.getWorld(), feet)) {
            if (!swing.ignoresCollision(entity) && !swing.worldCollisionShapes(feet).isEmpty()) {
                cir.setReturnValue(false);
                return;
            }
        }
    }

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
        return monvhua$interactedSwing != null ? monvhua$interactedSwing.canPlayerReachStructure(player)
                : player.canInteractWithEntityIn(bounds, extraRange);
    }
}
