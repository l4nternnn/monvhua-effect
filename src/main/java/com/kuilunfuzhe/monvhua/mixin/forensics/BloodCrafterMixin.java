package com.kuilunfuzhe.monvhua.mixin.forensics;
import com.kuilunfuzhe.monvhua.features.forensics.blood.BloodTransferService;
import net.minecraft.block.*;
import net.minecraft.block.entity.CrafterBlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(CrafterBlock.class)
public abstract class BloodCrafterMixin {
    @Inject(method = "craft", at = @At("HEAD"), cancellable = true)
    private void monvhua$preserveResidue(BlockState state, ServerWorld world, BlockPos pos, CallbackInfo ci) {
        if (world.getBlockEntity(pos) instanceof CrafterBlockEntity crafter)
            for (int i = 0; i < crafter.size(); i++) if (BloodTransferService.stained(crafter.getStack(i))) { ci.cancel(); return; }
    }
}
