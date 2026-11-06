package com.kuilunfuzhe.monvhua.mixin.forensics;

import com.kuilunfuzhe.monvhua.features.forensics.blood.BloodTransferService;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Block unsupported transformations at their result, while allowing inputs to be recovered. */
@Mixin(ScreenHandler.class)
public abstract class BloodProcessingMixin {
    @Inject(method = "onSlotClick", at = @At("HEAD"), cancellable = true)
    private void monvhua$protectEvidence(int slot, int button, SlotActionType action, PlayerEntity player, CallbackInfo ci) {
        if (player.getWorld().isClient) return;
        ScreenHandler handler = (ScreenHandler)(Object)this;
        int result = -1, start = 0, end = 0;
        if (handler instanceof AnvilScreenHandler || handler instanceof GrindstoneScreenHandler) { result = 2; end = 2; }
        else if (handler instanceof SmithingScreenHandler) { result = 3; end = 3; }
        else if (handler instanceof CraftingScreenHandler) { result = 0; start = 1; end = 10; }
        else if (handler instanceof PlayerScreenHandler) { result = 0; start = 1; end = 5; }
        if (result < 0 || (slot != result && action != SlotActionType.PICKUP_ALL)) return;
        boolean stained = false;
        for (int i = start; i < end; i++) stained |= BloodTransferService.stained(handler.getSlot(i).getStack());
        if (!stained) return;
        if (handler instanceof AnvilScreenHandler && handler.getSlot(1).getStack().isEmpty()) {
            var original = handler.getSlot(0).getStack(); var output = handler.getSlot(2).getStack();
            if (!output.isEmpty()) {
                var data = output.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
                data.putString(BloodTransferService.ITEM_REF, BloodTransferService.itemRef(original));
                output.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(data));
            }
            return;
        }
        player.sendMessage(net.minecraft.text.Text.literal("带血物品暂不支持此加工，请取回原物品；单件改名可以保留残留"), true);
        ci.cancel();
    }
}
