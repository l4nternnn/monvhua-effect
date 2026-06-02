package com.kuilunfuzhe.monvhua.mixin.carrymodelpose;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Kept only to avoid stale source references. This mixin is not registered in monvhua.client.mixins.json.
 *
 * <p>The carried player's look direction is intentionally no longer clamped. The rendered carried model head derives
 * a clamped visual rotation from the player's free camera view instead.</p>
 */
@Mixin(Entity.class)
public abstract class CarriedPlayerLookClampMixin {
}
