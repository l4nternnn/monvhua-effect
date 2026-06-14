package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.gravity.GravityMagic;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BucketItem;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BucketItem.class)
public abstract class InvertedBucketItemRaycastMixin {
    @Redirect(
            method = "use",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/item/BucketItem;raycast(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/world/RaycastContext$FluidHandling;)Lnet/minecraft/util/hit/BlockHitResult;"
            )
    )
    private BlockHitResult monvhua$raycastFromInvertedEye(World world, PlayerEntity player, RaycastContext.FluidHandling fluidHandling) {
        if (!GravityMagic.isInInvertedArea(player)) {
            return monvhua$raycast(world, player, player.getEyePos(), fluidHandling);
        }

        Vec3d start = new Vec3d(
                player.getX(),
                player.getY() + player.getHeight() - player.getEyeHeight(player.getPose()),
                player.getZ()
        );
        return monvhua$raycast(world, player, start, fluidHandling);
    }

    private static BlockHitResult monvhua$raycast(World world, PlayerEntity player, Vec3d start, RaycastContext.FluidHandling fluidHandling) {
        Vec3d end = start.add(player.getRotationVector(player.getPitch(), player.getYaw()).multiply(player.getBlockInteractionRange()));
        return world.raycast(new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.OUTLINE,
                fluidHandling,
                player
        ));
    }
}
