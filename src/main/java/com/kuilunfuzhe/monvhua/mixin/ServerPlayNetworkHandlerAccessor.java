package com.kuilunfuzhe.monvhua.mixin;

import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes vanilla's unconfirmed client teleport state to server-side constraints. */
@Mixin(ServerPlayNetworkHandler.class)
public interface ServerPlayNetworkHandlerAccessor {
    @Accessor("requestedTeleportPos")
    Vec3d monvhua$getRequestedTeleportPos();

    @Accessor("requestedTeleportId")
    int monvhua$getRequestedTeleportId();
}
