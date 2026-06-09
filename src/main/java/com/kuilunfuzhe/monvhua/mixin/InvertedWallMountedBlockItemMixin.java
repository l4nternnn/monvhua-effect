package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.gravity.GravityMagic;
import net.minecraft.block.BlockState;
import net.minecraft.block.WallMountedBlock;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.property.Properties;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class InvertedWallMountedBlockItemMixin {
    @Inject(method = "getPlacementState", at = @At("RETURN"), cancellable = true)
    private void monvhua$flipInvertedWallMountedPlacement(ItemPlacementContext context, CallbackInfoReturnable<BlockState> cir) {
        BlockState state = cir.getReturnValue();
        World world = context.getWorld();
        if (state == null
                || !(state.getBlock() instanceof WallMountedBlock)
                || !state.contains(Properties.BLOCK_FACE)
                || !GravityMagic.isInInvertedArea(world.getRegistryKey(), context.getBlockPos().toCenterPos())) {
            return;
        }

        BlockFace face = state.get(Properties.BLOCK_FACE);
        if (face == BlockFace.FLOOR) {
            cir.setReturnValue(state.with(Properties.BLOCK_FACE, BlockFace.CEILING));
        } else if (face == BlockFace.CEILING) {
            cir.setReturnValue(state.with(Properties.BLOCK_FACE, BlockFace.FLOOR));
        }
    }
}
