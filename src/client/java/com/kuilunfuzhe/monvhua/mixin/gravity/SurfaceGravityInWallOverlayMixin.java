package com.kuilunfuzhe.monvhua.mixin.gravity;

import com.kuilunfuzhe.monvhua.features.gravity.GravityMagic;
import com.kuilunfuzhe.monvhua.features.gravity.SurfaceGravityBasis;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InGameOverlayRenderer.class)
public abstract class SurfaceGravityInWallOverlayMixin {
    @Inject(method = "getInWallBlockState", at = @At("HEAD"), cancellable = true)
    private static void monvhua$getSurfaceGravityInWallBlockState(PlayerEntity player, CallbackInfoReturnable<BlockState> cir) {
        Direction downDirection = GravityMagic.getSurfaceGravityDirection(player);
        if (downDirection == null || downDirection == Direction.DOWN) {
            return;
        }

        Vec3d eye = GravityMagic.getSurfaceEyePos(player, 1.0F);
        Vec3d look = GravityMagic.getSurfaceLook(player);
        Vec3d down = SurfaceGravityBasis.directionVector(downDirection);
        Vec3d right = down.crossProduct(look);
        SurfaceGravityBasis.Basis basis = SurfaceGravityBasis.of(downDirection);
        if (right.lengthSquared() < 1.0E-8D) {
            right = basis.right();
        } else {
            right = right.normalize();
        }

        Vec3d up = right.crossProduct(look);
        if (up.lengthSquared() < 1.0E-8D) {
            up = basis.up();
        } else {
            up = up.normalize();
        }

        World world = player.getWorld();
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        double radius = player.getWidth() * 0.4D;
        double verticalRadius = player.getScale() * 0.05D;
        for (int i = 0; i < 8; ++i) {
            double rightOffset = (((i >> 0) % 2) - 0.5D) * radius;
            double upOffset = (((i >> 1) % 2) - 0.5D) * verticalRadius;
            double forwardOffset = (((i >> 2) % 2) - 0.5D) * 0.1D;
            Vec3d sample = eye
                    .add(right.multiply(rightOffset))
                    .add(up.multiply(upOffset))
                    .add(look.multiply(forwardOffset));
            mutable.set(sample.x, sample.y, sample.z);

            BlockState state = world.getBlockState(mutable);
            if (monvhua$blocksVisionForOverlay(state, world, mutable)) {
                cir.setReturnValue(state);
                return;
            }
        }

        cir.setReturnValue(null);
    }

    @Unique
    private static boolean monvhua$blocksVisionForOverlay(BlockState state, World world, BlockPos pos) {
        return state.getRenderType() != BlockRenderType.INVISIBLE && state.shouldBlockVision(world, pos);
    }
}
